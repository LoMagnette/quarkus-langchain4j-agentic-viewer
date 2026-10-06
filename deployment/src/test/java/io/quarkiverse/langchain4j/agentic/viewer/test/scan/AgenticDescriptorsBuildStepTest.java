package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * In a real application build, the extension produces a descriptor for every agentic annotation of the fixtures,
 * including supervisor, planner and human-in-the-loop agents.
 */
public class AgenticDescriptorsBuildStepTest {

    // the build step and the test method run in different class loaders: report through a system property
    private static final String RESULT_PROPERTY = "agentic-viewer.test.descriptors";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ScanFixtures.class))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                String summary = String.join("\n", context.consume(AgenticDescriptorsBuildItem.class).getDescriptors().stream()
                        .map(d -> d.id().substring(d.id().lastIndexOf('$') + 1) + " " + d.kind() + " out=" + d.outputKey()
                                + " in=" + d.inputKeys() + " sub="
                                + d.subAgents().stream().map(s -> s.substring(s.lastIndexOf('$') + 1)).toList())
                        .toList());
                System.setProperty(RESULT_PROPERTY, summary);
                context.produce(new FeatureBuildItem("agentic-viewer-descriptors-check"));
            }).consumes(AgenticDescriptorsBuildItem.class).produces(FeatureBuildItem.class).build());

    @Test
    void descriptorForEveryAnnotation() {
        assertEquals(List.of(
                "Approval#ask HUMAN_IN_THE_LOOP out=approval in=[draft] sub=[]",
                "ApprovedStory#write SEQUENCE out=approval in=[topic] sub=[Writer, Approval]",
                "Bank#transact SUPERVISOR out=result in=[request] sub=[Teller]",
                "Batch#translateAll PARALLEL_MAPPER out=translations in=[items] sub=[Translator]",
                "DbSearch#searchDb AI_AGENT out=db in=[topic] sub=[]",
                "DraftPlanner#plan PLANNER out=draft in=[topic] sub=[Writer]",
                "Editor#edit AI_AGENT out=draft in=[draft] sub=[]",
                "Expert#answer AI_AGENT out=answer in=[topic] sub=[]",
                "Polisher#polish LOOP out=draft in=[draft] sub=[Scorer, Editor]",
                "ResearchFan#research PARALLEL out=research in=[topic] sub=[WebSearch, DbSearch]",
                "Reviewer#review AI_AGENT out=review in=[draft] sub=[]",
                "Router#route CONDITIONAL out=answer in=[topic] sub=[Expert]",
                "Scorer#score AI_AGENT out=Score in=[draft] sub=[]",
                "Shouter#shout NON_AI_AGENT out=shout in=[draft] sub=[]",
                "StoryFlow#write SEQUENCE out=review in=[topic] sub=[Writer, Reviewer]",
                "Teller#move AI_AGENT out=receipt in=[amount] sub=[]",
                "Translator#translate AI_AGENT out=translation in=[item] sub=[]",
                "WebSearch#searchWeb AI_AGENT out=web in=[topic] sub=[]",
                "Writer#write AI_AGENT out=draft in=[topic] sub=[]"),
                List.of(System.getProperty(RESULT_PROPERTY).split("\n")));
    }
}
