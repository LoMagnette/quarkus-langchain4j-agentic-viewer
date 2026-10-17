package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;

/**
 * The builder systems the application produces as CDI beans (T5.1, {@code builder-discovery=cdi}): the candidates
 * found at build time, created when asked (the Dev UI listing them), kept when they are an agent with no parent (a
 * sub-agent of another system is part of that system).
 */
@ApplicationScoped
public class BuilderSystems {

    private static final Logger LOG = Logger.getLogger(BuilderSystems.class);
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
                        candidate.member(), null), agent));
            }
        });
        return systems;
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
