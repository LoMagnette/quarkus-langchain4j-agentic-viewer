package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.UpstreamAgents;

/**
 * With quarkus-langchain4j-agentic's detection, the scanner describes the agents it detected, keeps what the viewer
 * knowingly adds (a registry agent, an agent method inherited by a subclass), and reports any other difference.
 */
class UpstreamSelectionTest {

    interface BaseReviewer {
        @Agent(outputKey = "review")
        @ModelName("fast")
        String review(@V("draft") String draft);
    }

    /** Inherits its agent method: both detect it. */
    interface StyleReviewer extends BaseReviewer {
    }

    public static class Shouter {
        @Agent(outputKey = "shout")
        public String shout(@V("draft") String draft) {
            return draft.toUpperCase();
        }
    }

    /** Inherits a non-AI agent method: langchain4j runs it, quarkus-langchain4j does not detect it. */
    public static class LoudShouter extends Shouter {
    }

    interface FromRegistry {
        @RegistryAgent("translator")
        String translate(@V("draft") String draft);
    }

    interface Flow {
        @SequenceAgent(outputKey = "review", subAgents = { StyleReviewer.class, LoudShouter.class, FromRegistry.class })
        String run(@V("draft") String draft);
    }

    private static final List<Class<?>> FIXTURES = List.of(BaseReviewer.class, StyleReviewer.class, Shouter.class,
            LoudShouter.class, FromRegistry.class, Flow.class);
    /** The agent annotations quarkus-langchain4j-agentic 1.14.2 detects (no @RegistryAgent). */
    private static final List<DotName> UPSTREAM_ANNOTATIONS = List.of(DotName.createSimple(Agent.class.getName()),
            DotName.createSimple(SequenceAgent.class.getName()));

    private static Index index() throws Exception {
        return Index.of(FIXTURES.toArray(Class<?>[]::new));
    }

    /** AgenticProcessor.detectAgents: interfaces with their sub-interfaces, classes alone; the first @ModelName. */
    private static UpstreamAgents upstream(Index index, String modelName) {
        Map<String, List<MethodInfo>> methods = new LinkedHashMap<>();
        for (DotName annotation : UPSTREAM_ANNOTATIONS) {
            for (AnnotationInstance instance : index.getAnnotations(annotation)) {
                MethodInfo method = instance.target().asMethod();
                ClassInfo type = method.declaringClass();
                methods.computeIfAbsent(type.name().toString(), k -> new ArrayList<>()).add(method);
                if (type.isInterface()) {
                    index.getAllKnownSubinterfaces(type.name()).forEach(sub -> methods
                            .computeIfAbsent(sub.name().toString(), k -> new ArrayList<>()).add(method));
                }
            }
        }
        Map<String, UpstreamAgents.AiAgent> ai = new HashMap<>();
        ai.put(BaseReviewer.class.getName(), new UpstreamAgents.AiAgent(modelName, null));
        ai.put(StyleReviewer.class.getName(), new UpstreamAgents.AiAgent(modelName, null));
        return new UpstreamAgents(methods, ai);
    }

    @Test
    void theyAgreeAndTheViewerKeepsWhatItAddsKnowingly() throws Exception {
        Index index = index();
        AgenticAnnotationScanner.Result result = new AgenticAnnotationScanner().scan(index, upstream(index, "fast"));

        assertEquals(List.of(), result.disagreements());
        assertEquals(new AgenticAnnotationScanner().scan(index), result.descriptors(),
                "the same descriptors, registry agent and inherited class method included");
    }

    @Test
    void theModelComesFromQuarkusLangChain4j() throws Exception {
        Index index = index();
        AgenticAnnotationScanner.Result result = new AgenticAnnotationScanner().scan(index, upstream(index, "smart"));

        assertEquals("smart", descriptor(result, StyleReviewer.class).aiDetails().modelName());
    }

    @Test
    void anyOtherDifferenceIsReported() throws Exception {
        Index index = index();
        UpstreamAgents detected = upstream(index, "fast");
        Map<String, List<MethodInfo>> methods = new LinkedHashMap<>(detected.agentMethods());
        methods.remove(Flow.class.getName());
        // a method upstream detects with an annotation the viewer does not know
        ClassInfo shouter = index.getClassByName(DotName.createSimple(Shouter.class.getName()));
        methods.put(Shouter.class.getName(), List.of(shouter.method("<init>")));
        AgenticAnnotationScanner.Result result = new AgenticAnnotationScanner().scan(index,
                new UpstreamAgents(methods, detected.aiAgents()));

        assertEquals(List.of(
                Flow.class.getName() + "#run: found by the viewer only, not shown",
                Shouter.class.getName() + "#<init>: detected by quarkus-langchain4j-agentic with no agent annotation the viewer knows",
                Shouter.class.getName() + "#shout: found by the viewer only, not shown"), result.disagreements());
        assertEquals(new AgenticAnnotationScanner().scan(index).size() - 2, result.descriptors().size(),
                "the agents only the viewer finds are left out");
    }

    private static AgentDescriptor descriptor(AgenticAnnotationScanner.Result result, Class<?> type) {
        return result.descriptors().stream().filter(d -> d.declaringClass().equals(type.getName())).findFirst().orElseThrow();
    }
}
