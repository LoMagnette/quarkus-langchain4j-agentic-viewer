package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentTree;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.ToolDescriptor;

/**
 * The build-time scanner produces descriptors for every declarative sample system, down to its leaf agents, and the
 * root detection lists exactly the sample systems.
 */
class SampleDescriptorsTest {

    private static List<AgentDescriptor> descriptors;
    private static Map<String, AgentDescriptor> byClass;

    @BeforeAll
    static void scan() throws Exception {
        // every compiled class of the samples package, nested ones included (not test-classes, which has this package too)
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        descriptors = new AgenticAnnotationScanner().scan(Index.of(samples));
        byClass = descriptors.stream().collect(Collectors.toMap(AgentDescriptor::declaringClass, Function.identity()));
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    @Test
    void everySampleSystemHasARootDescriptor() {
        assertEquals(Map.of(
                "BankSupervisor", "SUPERVISOR",
                "DraftReviewer", "PLANNER",
                "ExpertRouter", "SEQUENCE",
                "PhraseTranslator", "PARALLEL_MAPPER",
                "ResearchGatherer", "PARALLEL",
                "ResilientStoryWriter", "SEQUENCE",
                "StoryCreator", "SEQUENCE",
                "StoryWriter", "SEQUENCE",
                "TripPlanner", "SEQUENCE"),
                descriptors.stream()
                        .filter(d -> !d.declaringClass().contains("$"))
                        .collect(Collectors.toMap(d -> simpleName(d.declaringClass()), d -> d.kind().name())));
    }

    @Test
    void everySubAgentHasADescriptor() {
        List<String> missing = descriptors.stream()
                .flatMap(d -> d.subAgents().stream())
                .filter(subAgent -> !byClass.containsKey(subAgent))
                .toList();
        assertEquals(List.of(), missing);
    }

    @Test
    void everyAgentIsReachableFromASampleSystem() {
        List<String> referenced = descriptors.stream().flatMap(d -> d.subAgents().stream()).toList();
        List<String> orphans = descriptors.stream()
                .filter(d -> d.declaringClass().contains("$") && !referenced.contains(d.declaringClass()))
                .map(AgentDescriptor::id)
                .toList();
        assertEquals(List.of(), orphans);
        assertTrue(descriptors.size() > 9, descriptors::toString);
    }

    private static String render(AgentTree tree) {
        String self = tree.agentClass().substring(tree.agentClass().lastIndexOf('.') + 1);
        return tree.children().isEmpty() ? self
                : self + tree.children().stream().map(SampleDescriptorsTest::render).toList();
    }

    @Test
    void rootSystemsOfTheSampleApp() {
        assertEquals(List.of(
                "BankSupervisor[BankSupervisor$ExchangeAgent, BankSupervisor$WithdrawAgent, BankSupervisor$CreditAgent]",
                "DraftReviewer[DraftReviewer$Drafter, DraftReviewer$Reviewer]",
                "ExpertRouter[ExpertRouter$CategoryRouter, ExpertRouter$Experts[ExpertRouter$MedicalExpert,"
                        + " ExpertRouter$TechnicalExpert, ExpertRouter$LegalExpert]]",
                "PhraseTranslator[PhraseTranslator$Translator]",
                "ResearchGatherer[ResearchGatherer$WebSearcher, ResearchGatherer$DatabaseSearcher,"
                        + " ResearchGatherer$ArchiveSearcher]",
                "ResilientStoryWriter[ResilientStoryWriter$TopicWriter]",
                "StoryCreator[StoryCreator$Drafter, StoryCreator$StyleReviewLoop[StoryCreator$StyleScorer,"
                        + " StoryCreator$StyleEditor]]",
                "StoryWriter[StoryWriter$IntroWriter, StoryWriter$AdventureWriter]",
                "TripPlanner[TripPlanner$DestinationProposer, TripPlanner$TravelerApproval, TripPlanner$ItineraryWriter]"),
                new RootSystemDetector().detect(descriptors).stream().map(SampleDescriptorsTest::render).toList());
    }

    @Test
    void patternDetails() {
        assertEquals(5, byClass.get(StoryCreator.StyleReviewLoop.class.getName()).loopGuard().maxIterations());
        assertEquals(3, byClass.get(ExpertRouter.Experts.class.getName()).branchGuards().size(),
                "one activation condition per expert");
        assertEquals("phrases", byClass.get(PhraseTranslator.class.getName()).itemsProvider());
        assertTrue(byClass.get(DraftReviewer.class.getName()).plannerSupplier() != null);
        assertEquals(List.of("credit", "withdraw"), byClass.get(BankSupervisor.CreditAgent.class.getName()).aiDetails()
                .tools().stream().map(ToolDescriptor::name).sorted().toList());
    }
}
