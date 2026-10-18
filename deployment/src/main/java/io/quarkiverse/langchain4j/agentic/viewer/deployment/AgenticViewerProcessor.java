package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.builder.BuildCallTransformer;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.builder.BuilderSystemBeans;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.builder.BuilderSystemBeansBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.builder.MixedSystems;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticRootsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.AgenticSystemsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderCandidate;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderSystems;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunObservation;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.AgenticTopologyContributor;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SynthesisFinishedBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Main build processor for the Agentic Viewer extension.
 */
public class AgenticViewerProcessor {

    private static final String PATTERNS_PACKAGE = "io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.";

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
    AgenticSystemsBuildItem buildTopologies(AgenticRootsBuildItem roots, CombinedIndexBuildItem index,
            AgenticViewerBuildConfig config) {
        // planner classes come from the bytecode of the @PlannerSupplier methods, read through the build class loader
        PlannerTypeDetector plannerTypes = PlannerTypeDetector.of(index.getIndex(),
                Thread.currentThread().getContextClassLoader());
        return new AgenticSystemsBuildItem(new TopologyBuilder(plannerTypes, config.loopWarningIterations())
                .build(roots.getRoots()));
    }

    /**
     * Hands the declarative systems to runtime-dev, where runs and their events will refer to the same node ids.
     * Dev mode only: the recorder lives in the runtime-dev artifact, which is absent from prod builds.
     */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
    @Record(ExecutionTime.STATIC_INIT)
    void recordSystems(AgenticSystemsBuildItem systems, AgenticRootsBuildItem roots, CombinedIndexBuildItem index,
            SynthesisFinishedBuildItem beans, BuilderSystemBeansBuildItem builderSystemBeans, AgenticViewerBuildConfig config,
            AgenticViewerRecorder recorder) {
        boolean builderDiscovery = config.builderDiscovery() != AgenticViewerBuildConfig.BuilderDiscovery.NONE;
        BuilderSystemBeans builderBeans = builderSystemBeans.getBeans();
        // a declarative system whose agent injects a builder system is MIXED (T5.5b); builder systems sharing agents
        // with the declarative ones are told at runtime, from the node of each agent class
        Map<String, String> agentClasses = TopologyBuilder.agentClasses(roots.getRoots());
        Set<String> mixed = builderDiscovery ? MixedSystems.declarativeUsingBuilders(agentClasses, beans, builderBeans)
                : Set.of();
        recorder.setSystems(systems.getSystems().stream().map(system -> mixed.contains(system.id())
                ? new AgenticSystem(system.id(), system.name(), system.rootNodeId(), SystemOrigin.MIXED,
                        system.sourceRef(), system.inputs(), system.root(), system.warnings())
                : system).toList());
        Map<String, String> declarativeAgents = new LinkedHashMap<>();
        agentClasses.forEach((nodeId, agentClass) -> declarativeAgents.putIfAbsent(agentClass, nodeId));
        recorder.setDeclarativeAgents(declarativeAgents);
        recorder.setLoopWarningIterations(config.loopWarningIterations());
        // the application's topology contributors, created and applied in dev mode only (runtime-dev)
        recorder.setTopologyContributors(index.getIndex()
                .getAllKnownImplementors(DotName.createSimple(AgenticTopologyContributor.class.getName())).stream()
                .filter(type -> !type.isInterface() && !Modifier.isAbstract(type.flags()))
                .map(type -> type.name().toString()).sorted().toList());
        // the beans that may be builder systems, by bean identifier; after setSystems, which resets them
        recorder.setBuilderCandidates(!builderDiscovery ? List.of()
                : beans.getBeans().stream().filter(builderBeans::isBuilderSystem).map(builderBeans::candidate)
                        .sorted(Comparator.comparing(BuilderCandidate::declaringClass)
                                .thenComparing(BuilderCandidate::member))
                        .toList());
    }

    /**
     * The registry and run manager the JSON-RPC service uses, and the observation of the root agents; dev mode only,
     * like runtime-dev.
     */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
    AdditionalBeanBuildItem registerRuntimeDevBeans() {
        return AdditionalBeanBuildItem.builder().addBeanClasses(AgenticSystemRegistry.class, RunManager.class, RunObservation.class,
                BuilderSystems.class).setUnremovable().build();
    }

    /** One reader of the producers' bytecode per build, shared by the steps below. */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
    BuilderSystemBeansBuildItem builderSystemBeans() {
        return new BuilderSystemBeansBuildItem(BuilderSystemBeans.of(Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Keeps the beans that are builder systems (T5.1) even when the application does not inject them: the Dev UI lists
     * them. Nothing creates them before the Dev UI asks.
     */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class, BuilderDiscoveryCdi.class })
    UnremovableBeanBuildItem keepBuilderSystemBeans(BuilderSystemBeansBuildItem builderSystemBeans) {
        return new UnremovableBeanBuildItem(builderSystemBeans.getBeans()::isBuilderSystem);
    }

    /**
     * builder-discovery=transform (T5.4): rewrites the application classes calling a builder's build(), so every system
     * the application builds is reported with its call site. Dev mode only: the registry lives in runtime-dev.
     */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class, BuilderDiscoveryTransform.class })
    void transformBuildCalls(ApplicationIndexBuildItem applicationIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String className : BuildCallTransformer.classesToTransform(applicationIndex.getIndex(),
                name -> classLoader.getResourceAsStream(name.replace('.', '/') + ".class"))) {
            transformers.produce(new BytecodeTransformerBuildItem.Builder().setClassToTransform(className)
                    .setVisitorFunction(BuildCallTransformer::visitor).build());
        }
    }

    /**
     * Adapter of each pattern planner of langchain4j-agentic-patterns, by planner class. Names only: an adapter class
     * refers to the module, so it must not be loaded (here or at runtime) when the module is absent.
     */
    static final Map<String, String> PATTERN_ADAPTERS = Map.of(
            "dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner", PATTERNS_PACKAGE + "GoapAdapter",
            "dev.langchain4j.agentic.patterns.p2p.P2PPlanner", PATTERNS_PACKAGE + "P2PAdapter",
            "dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner", PATTERNS_PACKAGE + "BlackboardAdapter");

    /**
     * @param present whether a class is on the application class path
     * @return the adapters to register: those whose pattern planner is present (apps add langchain4j-agentic-patterns
     *         directly, there is no Quarkus extension for it)
     */
    static List<String> patternAdapters(Predicate<String> present) {
        return PATTERN_ADAPTERS.entrySet().stream().filter(entry -> present.test(entry.getKey()))
                .map(Map.Entry::getValue).sorted().toList();
    }

    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
    AdditionalBeanBuildItem registerPatternAdapters() {
        return AdditionalBeanBuildItem.builder().addBeanClass(PatternAdapters.class)
                .addBeanClasses(patternAdapters(QuarkusClassLoader::isClassPresentAtRuntime))
                .setUnremovable().build();
    }

    /**
     * Registers the Dev UI card and its page (only in dev mode).
     */
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
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
    @BuildStep(onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class })
    JsonRPCProvidersBuildItem registerJsonRpc() {
        return new JsonRPCProvidersBuildItem(AgenticViewerJsonRpcService.class);
    }
}
