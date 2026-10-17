package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.util.ArrayList;
import java.util.HashSet;
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

    @Inject
    RunObservation observation;
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
                systems.add(BuilderTopology.system(id, name(candidate), new SourceRef(candidate.declaringClass(),
                        candidate.member(), null), agent, BuilderSystems::declarativeNode));
                roots.put(id, agent);
                observation.observe(id, agent);
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
        Set<String> producers = new HashSet<>();
        AgenticViewerRecorder.getBuilderCandidates().forEach(c -> producers.add(c.declaringClass() + "#" + c.member()));
        List<AgenticSystem> systems = new ArrayList<>();
        BuiltSystems.latest().forEach((id, built) -> {
            AgentInstance agent = (AgentInstance) built;
            String callSite = id.contains("~") ? id.substring(0, id.indexOf('~')) : id;
            String producer = callSite.substring(0, callSite.lastIndexOf(':'));
            if (agent.parent() != null || producers.contains(producer) || roots.get(id) == built) {
                return;
            }
            String className = producer.substring(0, producer.indexOf('#'));
            String method = producer.substring(producer.indexOf('#') + 1);
            String name = agent.name() != null && !agent.name().isBlank() ? agent.name()
                    : BuilderTopology.simpleName(className) + "." + method;
            systems.add(BuilderTopology.system(id, name, new SourceRef(className, method, callSite), agent,
                    BuilderSystems::declarativeNode));
            roots.put(id, built);
            observation.observe(id, built);
        });
        return systems;
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
