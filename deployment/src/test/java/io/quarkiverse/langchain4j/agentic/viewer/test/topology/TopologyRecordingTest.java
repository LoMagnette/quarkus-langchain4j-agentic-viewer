package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.deployment.builditem.StaticBytecodeRecorderBuildItem;
import io.quarkus.deployment.recording.BytecodeRecorderImpl;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Topology values can be recorded at build time: a build step records them through the bytecode recorder,
 * and the application gets equal values back at runtime.
 */
public class TopologyRecordingTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(TopologyTestRecorder.class, TopologyFixtures.class))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                BytecodeRecorderImpl recorder = new BytecodeRecorderImpl(true, TopologyRecordingTest.class.getSimpleName(),
                        "recordTopology", "topology-recording-test", false);
                // the recorder class must come from the build's class loader, not from the one that loaded this test
                try {
                    Class<?> recorderClass = Thread.currentThread().getContextClassLoader()
                            .loadClass(TopologyTestRecorder.class.getName());
                    Object proxy = recorder.getRecordingProxy(recorderClass);
                    recorderClass.getMethod("record", List.class).invoke(proxy,
                            List.of(TopologyFixtures.storyCreator(), TopologyFixtures.routerWithPlanner()));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                context.produce(new StaticBytecodeRecorderBuildItem(recorder));
            }).produces(StaticBytecodeRecorderBuildItem.class).build());

    @Test
    void recordedTopologyEqualsTheOriginal() {
        // runs in the application class loader, like the recorded bytecode
        assertEquals(List.of(TopologyFixtures.storyCreator(), TopologyFixtures.routerWithPlanner()),
                TopologyTestRecorder.recorded);
    }
}
