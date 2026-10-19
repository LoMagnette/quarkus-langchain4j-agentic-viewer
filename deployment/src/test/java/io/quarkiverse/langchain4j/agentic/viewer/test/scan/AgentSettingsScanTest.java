package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentSettings;

/**
 * Compensation, summarized context and supervisor strategies are kept as declared, with the annotation defaults.
 */
class AgentSettingsScanTest {

    interface Payer {
        @Agent(outputKey = "receipt", compensateOnError = true, summarizedContext = { "checker", " " })
        String pay(@V("amount") String amount);
    }

    interface Checker {
        @Agent(outputKey = "check")
        String check(@V("amount") String amount);
    }

    interface Cashier {
        @SupervisorAgent(outputKey = "receipt", subAgents = { Payer.class, Checker.class }, maxAgentsInvocations = 3,
                contextStrategy = SupervisorContextStrategy.SUMMARIZATION, responseStrategy = SupervisorResponseStrategy.SCORED,
                compensateOnError = true)
        String serve(@V("amount") String amount);
    }

    interface Desk {
        @SupervisorAgent(outputKey = "receipt", subAgents = { Checker.class })
        String serve(@V("amount") String amount);
    }

    interface Retry {
        @LoopAgent(outputKey = "receipt", subAgents = { Payer.class }, maxIterations = 2, compensateOnError = true)
        String retry(@V("amount") String amount);
    }

    interface Flow {
        @SequenceAgent(outputKey = "check", subAgents = { Checker.class })
        String run(@V("amount") String amount);
    }

    private static Map<String, AgentDescriptor> byClass;

    @BeforeAll
    static void scan() throws Exception {
        byClass = new AgenticAnnotationScanner().scan(Index.of(Payer.class, Checker.class, Cashier.class, Desk.class,
                Retry.class, Flow.class)).stream().collect(Collectors.toMap(AgentDescriptor::declaringClass, Function.identity()));
    }

    private static AgentSettings settings(Class<?> type) {
        return byClass.get(type.getName()).settings();
    }

    @Test
    void agentCompensationAndSummarizedContext() {
        assertEquals(new AgentSettings(true, List.of("checker"), null, null, null), settings(Payer.class));
        assertEquals(new AgentSettings(false, List.of(), null, null, null), settings(Checker.class));
    }

    @Test
    void supervisorStrategies() {
        assertEquals(new AgentSettings(true, List.of(), 3, "SUMMARIZATION", "SCORED"), settings(Cashier.class));
        assertEquals(new AgentSettings(false, List.of(), 10, "CHAT_MEMORY", "LAST"), settings(Desk.class), "the defaults");
    }

    @Test
    void workflowCompensation() {
        assertEquals(new AgentSettings(true, List.of(), null, null, null), settings(Retry.class));
        assertEquals(new AgentSettings(false, List.of(), null, null, null), settings(Flow.class));
    }

    @Test
    void theTopologyKeepsThem() {
        AgentNode cashier = new TopologyBuilder().build(new RootSystemDetector().detect(byClass.values().stream().toList()))
                .stream().filter(system -> system.name().equals("Cashier")).findFirst().orElseThrow().root();
        assertEquals(settings(Cashier.class), cashier.settings());
        assertEquals(settings(Payer.class), cashier.children().get(0).settings());
    }
}
