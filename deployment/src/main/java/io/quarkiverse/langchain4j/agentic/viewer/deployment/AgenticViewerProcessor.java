package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticRootsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.AgenticSystemsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
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
     * The registry and run manager the JSON-RPC service uses; dev mode only, like runtime-dev.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    AdditionalBeanBuildItem registerRuntimeDevBeans() {
        return AdditionalBeanBuildItem.builder().addBeanClasses(AgenticSystemRegistry.class, RunManager.class)
                .setUnremovable().build();
    }

    /**
     * Registers the Dev UI card and its page (only in dev mode).
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem registerDevUI(AgenticSystemsBuildItem systems) {
        CardPageBuildItem card = new CardPageBuildItem();

        // Agentic systems page (T1.5), fed over JSON-RPC (T2.1): the card shows the number of systems known at build
        // time as the label of its link
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
