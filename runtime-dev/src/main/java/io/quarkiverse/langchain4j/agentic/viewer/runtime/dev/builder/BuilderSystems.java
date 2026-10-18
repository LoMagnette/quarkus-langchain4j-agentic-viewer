package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunObservation;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;

/**
 * The builder systems the application produces as CDI beans (T5.1, {@code builder-discovery=cdi}): the candidates
 * found at build time, created when asked (the Dev UI listing them), kept when they are an agent with no parent (a
 * sub-agent of another system is part of that system), and observed for the live view of their runs.
 */
@ApplicationScoped
public class BuilderSystems {

    private static final Logger LOG = Logger.getLogger(BuilderSystems.class);

    // the root agent of each system found, by system id: what a run invokes
    private final Map<String, Object> roots = new ConcurrentHashMap<>();
    // reported systems that could not be shown, by entry id: not tried again until rebuilt
    private final Map<String, Object> failed = new ConcurrentHashMap<>();

    @Inject
    RunObservation observation;

    public BuilderSystems() {
    }

    /** Outside CDI (tests). */
    public BuilderSystems(RunObservation observation) {
        this.observation = observation;
    }
    private static final Set<String> UNTYPED = Set.of("dev.langchain4j.agentic.UntypedAgent",
            "dev.langchain4j.agentic.supervisor.SupervisorAgent", "dev.langchain4j.agentic.planner.AgentInstance");

    /**
     * @return the root builder systems, in build-time order; candidates that cannot be created are logged and skipped
     */
    public List<AgenticSystem> discover() {
        // all created first: a system sets itself as the parent of its sub-agents when it is built
        Map<BuilderCandidate, AgentInstance> agents = new LinkedHashMap<>();
        for (BuilderCandidate candidate : AgenticViewerRecorder.getBuilderCandidates()) {
            try {
                AgentInstance agent = agent(candidate);
                if (agent != null) {
                    agents.put(candidate, agent);
                }
            } catch (RuntimeException e) {
                LOG.warnf(e, "Agentic viewer: cannot create the bean %s#%s to show its agentic system",
                        candidate.declaringClass(), candidate.member());
            }
        }
        List<AgenticSystem> systems = new ArrayList<>();
        agents.forEach((candidate, agent) -> {
            if (agent.parent() == null) {
                String id = candidate.declaringClass() + "#" + candidate.member();
                try {
                    systems.add(BuilderTopology.system(id, name(candidate), new SourceRef(candidate.declaringClass(),
                            candidate.member(), null), agent, BuilderSystems::declarativeNode));
                    roots.put(id, agent);
                    observation.observe(id, agent);
                } catch (RuntimeException e) {
                    LOG.warnf(e, "Agentic viewer: cannot show the agentic system of %s", id);
                }
            }
        });
        return systems;
    }

    /**
     * The systems the transform reported (builder-discovery=transform, T5.4) that are new since the last call, or
     * rebuilt (a newer instance of a known entry): roots only, not those a CDI producer already shows. One entry per
     * call site and shape (T5.5a): a system built per request is one entry, its latest instance is what a run invokes.
     *
     * @return the systems to register or replace, by id {@code Class#method:line}, {@code ~n} for the n-th shape
     */
    public synchronized List<AgenticSystem> built() {
        List<BuilderCandidate> candidates = AgenticViewerRecorder.getBuilderCandidates();
        Set<Object> cdiRoots = Collections.newSetFromMap(new IdentityHashMap<>());
        cdiRoots.addAll(roots.values());
        List<AgenticSystem> systems = new ArrayList<>();
        BuiltSystems.latest().forEach((id, built) -> {
            AgentInstance agent = (AgentInstance) built;
            String callSite = id.contains("~") ? id.substring(0, id.indexOf('~')) : id;
            String producer = callSite.substring(0, callSite.lastIndexOf(':'));
            if (agent.parent() != null || roots.get(id) == built || failed.get(id) == built
                    || (!roots.containsKey(id) && (cdiRoots.contains(built) || isProducer(producer, candidates)))) {
                return;
            }
            String className = producer.substring(0, producer.indexOf('#'));
            String method = producer.substring(producer.indexOf('#') + 1);
            String name = agent.name() != null && !agent.name().isBlank() ? agent.name()
                    : BuilderTopology.simpleName(className) + "." + method;
            try {
                systems.add(BuilderTopology.system(id, name, new SourceRef(className, method, callSite), agent,
                        BuilderSystems::declarativeNode));
                roots.put(id, built);
                failed.remove(id);
                observation.observe(id, built);
            } catch (RuntimeException e) {
                // one system that cannot be shown must not hide the others
                failed.put(id, built);
                LOG.warnf(e, "Agentic viewer: cannot show the agentic system built at %s", callSite);
            }
        });
        return systems;
    }

    /**
     * Whether a call site ({@code Class#method}) is the build of a CDI producer, which discover() already shows: the
     * producer method, a lambda in it, or the constructor (static initializer) setting a producer field.
     */
    private static boolean isProducer(String callSite, List<BuilderCandidate> candidates) {
        String className = callSite.substring(0, callSite.indexOf('#'));
        String method = callSite.substring(callSite.indexOf('#') + 1);
        for (BuilderCandidate candidate : candidates) {
            if (!candidate.declaringClass().equals(className)) {
                continue;
            }
            if (method.equals(candidate.member()) || method.startsWith("lambda$" + candidate.member() + "$")) {
                return true;
            }
            if ((method.equals("<init>") || method.equals("<clinit>")) && isField(className, candidate.member())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isField(String className, String member) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader()).getDeclaredField(member);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * @return the ids of the systems shown so far that became a sub-agent of another system since (built first, adopted
     *         by a later build): no longer systems of their own; reported once
     */
    public synchronized List<String> stale() {
        List<String> stale = new ArrayList<>();
        roots.forEach((id, root) -> {
            if (root instanceof AgentInstance agent && agent.parent() != null) {
                stale.add(id);
            }
        });
        stale.forEach(roots::remove);
        return stale;
    }

    @PreDestroy
    void forgetBuiltSystems() {
        BuiltSystems.clear();
    }

    /**
     * @return the root agent of a builder system found by {@link #discover()} or {@link #built()}
     */
    public Optional<Object> root(String systemId) {
        return Optional.ofNullable(roots.get(systemId));
    }

    private static AgentInstance agent(BuilderCandidate candidate) {
        InjectableBean<?> bean = Arc.container().bean(candidate.beanId());
        if (bean == null) {
            return null;
        }
        Object instance = Arc.container().instance(bean).get();
        Object agent = instance instanceof ClientProxy proxy ? ClientProxy.unwrap(proxy) : instance;
        return agent instanceof AgentInstance agentInstance ? agentInstance : null;
    }

    /** The node of an agent class in the declarative systems (build time), null when none uses it. */
    static AgentNode declarativeNode(String agentClass) {
        String nodeId = AgenticViewerRecorder.getDeclarativeAgents().get(agentClass);
        if (nodeId == null) {
            return null;
        }
        for (AgenticSystem system : AgenticViewerRecorder.getSystems()) {
            AgentNode node = find(system.root(), nodeId);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private static AgentNode find(AgentNode node, String id) {
        if (node.id().equals(id)) {
            return node;
        }
        for (AgentNode child : node.children()) {
            AgentNode found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The bean name, else the produced interface, else the producer. */
    static String name(BuilderCandidate candidate) {
        if (candidate.beanName() != null) {
            return candidate.beanName();
        }
        if (!UNTYPED.contains(candidate.typeName())) {
            return BuilderTopology.simpleName(candidate.typeName());
        }
        return BuilderTopology.simpleName(candidate.declaringClass()) + "." + candidate.member();
    }
}
