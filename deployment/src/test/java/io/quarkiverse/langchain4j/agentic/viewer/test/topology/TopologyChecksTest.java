package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.TopologyChecks;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.TopologyWarning;

/**
 * The checks of a system's wiring (TopologyChecks): warnings for what will not behave as written, hints for what
 * likely does not, silenced where the topology cannot know.
 */
class TopologyChecksTest {

    private static AgentNode leaf(String id, NodeKind kind, List<String> reads, String writes) {
        return new AgentNode(id, id, kind, null, reads, writes, false, false, List.of(), null, null, null, null);
    }

    private static AgentNode workflow(String id, NodeKind kind, List<String> reads, String writes, Guard guard,
            AgentNode... children) {
        return new AgentNode(id, id, kind, null, reads, writes, false, false, List.of(children), guard, null, null, null);
    }

    private static List<String> found(List<TopologyWarning> warnings) {
        return warnings.stream().map(w -> w.level() + " " + w.code() + " " + w.nodeId()).toList();
    }

    @Test
    void aLoopWithoutExitConditionAboveTheLimit() {
        AgentNode edit = leaf("r/loop/edit", NodeKind.AI_AGENT, List.of("draft"), "draft");
        AgentNode root = workflow("r", NodeKind.SEQUENCE, List.of("draft"), "draft", null,
                workflow("r/loop", NodeKind.LOOP, List.of("draft"), "draft", Guard.loopExit(null, 10, false), edit));
        assertEquals(List.of("WARNING loop-without-exit r/loop"), found(TopologyChecks.check(root, Map.of(), Set.of(), 5)));
        assertEquals(List.of(), found(TopologyChecks.check(root, Map.of(), Set.of(), 10)), "not above the limit");

        AgentNode withExit = workflow("r", NodeKind.SEQUENCE, List.of("draft"), "draft", null,
                workflow("r/loop", NodeKind.LOOP, List.of("draft"), "draft", Guard.loopExit("goodEnough", 10, false), edit));
        assertEquals(List.of(), found(TopologyChecks.check(withExit, Map.of(), Set.of(), 5)));
    }

    @Test
    void aConditionalBranchWithoutCondition() {
        AgentNode root = workflow("r", NodeKind.CONDITIONAL, List.of("topic"), "answer", null,
                leaf("r/Expert", NodeKind.AI_AGENT, List.of("topic"), "answer"));
        List<TopologyWarning> warnings = TopologyChecks.check(root, Map.of("r", List.of("com.example.App$Writer")),
                Set.of(), 5);
        assertEquals(List.of("WARNING branch-without-condition r"), found(warnings));
        assertEquals("Writer has no @ActivationCondition: langchain4j leaves it out of the conditional, it never runs",
                warnings.get(0).message());
    }

    @Test
    void outputsNobodyReads() {
        AgentNode root = workflow("r", NodeKind.SEQUENCE, List.of("topic"), "story", null,
                leaf("r/notes", NodeKind.AI_AGENT, List.of("topic"), "notes"),
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic"), "story"));
        assertEquals(List.of("HINT output-never-read r/notes"), found(TopologyChecks.check(root, Map.of(), Set.of(), 5)),
                "the last agent writes what the workflow returns: read by the caller");
        assertEquals(List.of(), found(TopologyChecks.check(root, Map.of(), Set.of("notes"), 5)), "read by a condition");
        assertEquals(List.of(), found(TopologyChecks.check(root, Map.of(), null, 5)), "reads unknown (builder lambdas)");
        AgentNode supervised = workflow("r", NodeKind.SUPERVISOR, List.of("topic"), "story", null,
                leaf("r/notes", NodeKind.AI_AGENT, List.of("topic"), "notes"),
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic"), "story"));
        assertEquals(List.of(), found(TopologyChecks.check(supervised, Map.of(), Set.of(), 5)), "a supervisor decides");
    }

    @Test
    void keysNothingWrites() {
        AgentNode root = workflow("r", NodeKind.SEQUENCE, List.of("topic"), "story", null,
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic", "style"), "story"));
        assertEquals(List.of("HINT key-never-written r/write"), found(TopologyChecks.check(root, Map.of(), Set.of(), 5)));

        AgentNode withInput = workflow("r", NodeKind.SEQUENCE, List.of("topic", "style"), "story", null,
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic", "style"), "story"));
        assertEquals(List.of(), found(TopologyChecks.check(withInput, Map.of(), Set.of(), 5)), "a system input");

        AgentNode writtenInCode = workflow("r", NodeKind.SEQUENCE, List.of("topic"), "story", null,
                leaf("r/prepare", NodeKind.NON_AI_AGENT, List.of("topic"), null),
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic", "style"), "story"));
        assertEquals(List.of(), found(TopologyChecks.check(writtenInCode, Map.of(), Set.of(), 5)),
                "a non-AI agent without output key may write it in code");

        AgentNode planned = workflow("r", NodeKind.PLANNER, List.of("topic"), "story", null,
                leaf("r/write", NodeKind.AI_AGENT, List.of("topic", "style"), "story"));
        assertEquals(List.of(), found(TopologyChecks.check(planned, Map.of(), Set.of(), 5)), "a planner decides");
    }

    // ---- from a scan: typed keys, @Output and condition reads, mapper items ----

    class Story implements TypedKey<String> {
    }

    interface Drafter {
        @Agent(typedOutputKey = Story.class)
        String draft(@V("topic") String topic);
    }

    interface Polisher {
        @Agent(outputKey = "polished")
        String polish(@K(Story.class) String story);
    }

    interface Gate {
        @ConditionalAgent(outputKey = "polished", subAgents = { Polisher.class })
        String gate(@V("topic") String topic);

        @ActivationCondition(Polisher.class)
        static boolean worthIt(@V("score") double score) {
            return score > 0.5;
        }
    }

    interface Scorer {
        @Agent(outputKey = "score")
        double score(@K(Story.class) String story);
    }

    interface Translator {
        @Agent(outputKey = "translation")
        String translate(@V("phrase") String phrase);
    }

    interface Translations {
        @ParallelMapperAgent(outputKey = "translations", subAgent = Translator.class, itemsProvider = "phrases")
        String translateAll(@V("phrases") java.util.List<String> phrases);
    }

    interface Pipeline {
        @SequenceAgent(outputKey = "summary", subAgents = { Drafter.class, Scorer.class, Gate.class, Translations.class })
        String run(@V("topic") String topic, @V("phrases") java.util.List<String> phrases);

        @Output
        static String summary(@V("polished") String polished, @V("translations") String translations) {
            return polished + translations;
        }
    }

    /** Scorer has no activation condition: left out by langchain4j. */
    interface Router {
        @ConditionalAgent(outputKey = "polished", subAgents = { Polisher.class, Scorer.class })
        String route(@V("topic") String topic);

        @ActivationCondition(Polisher.class)
        static boolean always(@V("topic") String topic) {
            return true;
        }
    }

    interface Spinner {
        @LoopAgent(outputKey = "polished", subAgents = { Polisher.class })
        String spin(@V("topic") String topic);
    }

    @Test
    void aScannedSystemHasNoFalseHint() throws Exception {
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner()
                .scan(Index.of(Story.class, Drafter.class, Polisher.class, Gate.class, Scorer.class, Translator.class,
                        Translations.class, Pipeline.class, Spinner.class, Router.class))));
        AgenticSystem pipeline = systems.stream().filter(s -> s.name().equals("Pipeline")).findFirst().orElseThrow();
        // the typed key is written and read, score is read by the activation condition, polished and translations by
        // @Output, the mapper item key comes at runtime
        assertEquals(List.of(), found(pipeline.warnings()), pipeline.warnings()::toString);

        AgenticSystem spinner = systems.stream().filter(s -> s.name().equals("Spinner")).findFirst().orElseThrow();
        assertEquals(List.of("WARNING loop-without-exit " + spinner.id(), "HINT key-never-written " + spinner.id() + "/Polisher"),
                found(spinner.warnings()), "no exit condition and the default 10 iterations; nothing writes Story here");
    }

    @Test
    void aScannedConditionalBranchWithoutCondition() throws Exception {
        AgenticSystem router = new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner()
                .scan(Index.of(Story.class, Polisher.class, Scorer.class, Router.class)))).get(0);
        assertEquals("WARNING branch-without-condition " + router.id(), found(router.warnings()).get(0));
    }

    @Test
    void builderSystemsAreCheckedTheSameWay() {
        Object noExit = dev.langchain4j.agentic.AgenticServices.loopBuilder().maxIterations(8)
                .subAgents(dev.langchain4j.agentic.AgenticServices.agentAction(scope -> scope.writeState("x", 1))).build();
        Object withExit = dev.langchain4j.agentic.AgenticServices.loopBuilder().maxIterations(8)
                .exitCondition(scope -> scope.hasState("x"))
                .subAgents(dev.langchain4j.agentic.AgenticServices.agentAction(scope -> scope.writeState("x", 1))).build();
        assertEquals(List.of("WARNING loop-without-exit spin"), found(builder(noExit).warnings()));
        assertEquals(List.of(), found(builder(withExit).warnings()), "a lambda exit condition");
    }

    private static AgenticSystem builder(Object root) {
        return io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology.system("spin", "spin",
                new io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef("App", "spin", null),
                (dev.langchain4j.agentic.planner.AgentInstance) root);
    }
}
