package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticRootsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.AgenticSystemsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Main build processor for the Agentic Viewer extension.
 */
public class AgenticViewerProcessor {

    private static final String FEATURE = "langchain4j-agentic-viewer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Describes every agent method of the application (T1.2).
     */
    @BuildStep
    AgenticDescriptorsBuildItem scanAgenticAnnotations(CombinedIndexBuildItem combinedIndex) {
        return new AgenticDescriptorsBuildItem(new AgenticAnnotationScanner().scan(combinedIndex.getIndex()));
    }

    /**
     * Keeps the agents no other agent uses as roots of agentic systems, with their sub-agents nested (T1.3).
     */
    @BuildStep
    AgenticRootsBuildItem detectRootSystems(AgenticDescriptorsBuildItem descriptors) {
        return new AgenticRootsBuildItem(new RootSystemDetector().detect(descriptors.getDescriptors()));
    }

    /**
     * Builds the topology of every declarative system (T1.4).
     */
    @BuildStep
    AgenticSystemsBuildItem buildTopologies(AgenticRootsBuildItem roots) {
        return new AgenticSystemsBuildItem(new TopologyBuilder().build(roots.getRoots()));
    }

    /**
     * Hands the declarative systems to runtime-dev, where runs and their events will refer to the same node ids.
     * Dev mode only: the recorder lives in the runtime-dev artifact, which is absent from prod builds.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.STATIC_INIT)
    void recordSystems(AgenticSystemsBuildItem systems, AgenticViewerRecorder recorder) {
        recorder.setSystems(systems.getSystems());
    }

    /**
     * Registers the Dev UI card and its page (only in dev mode).
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem registerDevUI(AgenticSystemsBuildItem systems) {
        CardPageBuildItem card = new CardPageBuildItem();

        // Topology of the declarative systems (T1.4), for the system list and graph
        card.addBuildTimeData("agenticSystems", systems.getSystems());

        // Agentic systems page (T1.5): the card shows the number of systems as the label of its link
        card.addPage(Page.webComponentPageBuilder()
                .title("Agentic Systems")
                .icon("font-awesome-solid:robot")
                .staticLabel(String.valueOf(systems.getSystems().size()))
                .componentLink("qwc-agentic-systems.js"));

        return card;
    }

    /**
     * Registers the JSON-RPC service used by the Dev UI pages (only in dev mode, like the service itself).
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem registerJsonRpc() {
        return new JsonRPCProvidersBuildItem(AgenticViewerJsonRpcService.class);
    }
}
