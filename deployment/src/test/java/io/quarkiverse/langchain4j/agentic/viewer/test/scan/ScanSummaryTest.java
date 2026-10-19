package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentTree;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.ToolInfo;

/**
 * One line per descriptor of every agentic annotation of the fixtures, including supervisor, planner and
 * human-in-the-loop agents, loop and conditional guards, {@code @Output} and the details of an AI agent, and the root
 * systems they form. The scan only runs in dev mode builds (AgenticViewerOutsideDevModeTest), where the dev-mode tests
 * cover it through the Dev UI; here it runs over an index of the fixtures.
 */
class ScanSummaryTest {

    private static List<AgentDescriptor> descriptors;

    @BeforeAll
    static void scan() throws Exception {
        descriptors = new AgenticAnnotationScanner().scan(Index.of(ScanFixtures.class.getDeclaredClasses()));
    }

    private static String render(AgentTree tree) {
        String self = tree.agentClass().substring(tree.agentClass().lastIndexOf('$') + 1);
        return tree.children().isEmpty() ? self
                : self + tree.children().stream().map(ScanSummaryTest::render).toList();
    }

    @Test
    void rootSystems() {
        List<String> roots = new RootSystemDetector().detect(descriptors).stream().map(ScanSummaryTest::render).toList();
        assertEquals(List.of(
                "ApprovedStory[Writer, Approval]",
                "Bank[Teller]",
                "Batch[Translator]",
                "Cashier",
                "DraftPlanner[Writer]",
                "Polisher[Scorer, Editor]",
                "Repeater[Editor]",
                "ResearchFan[WebSearch, DbSearch]",
                "Router[Expert, Generalist]",
                "Shouter",
                "StoryFlow[Writer, Reviewer]"),
                roots);
    }

    private static String guards(AgentDescriptor d) {
        String guards = "";
        if (d.loopGuard() != null) {
            guards += " exit=" + d.loopGuard().label() + "/" + d.loopGuard().maxIterations() + "/"
                    + d.loopGuard().testExitAtLoopEnd();
        }
        if (!d.branchGuards().isEmpty()) {
            guards += " when=" + d.branchGuards().entrySet().stream()
                    .map(e -> e.getKey().substring(e.getKey().lastIndexOf('$') + 1) + ":" + e.getValue().label())
                    .toList();
        }
        if (d.outputMethod() != null) {
            guards += " output=" + d.outputMethod().substring(d.outputMethod().lastIndexOf('#') + 1);
        }
        if (d.optional() || d.async()) {
            guards += " optional=" + d.optional() + " async=" + d.async();
        }
        if (d.aiDetails() != null && d.aiDetails().modelName() != null) {
            AiAgentDetails ai = d.aiDetails();
            guards += " model=" + ai.modelName() + " tools=" + ai.tools().stream().map(ToolInfo::name).toList()
                    + " system=" + ai.systemMessage().lines().findFirst().orElse("") + " user=" + ai.userMessage();
        }
        return guards;
    }

    @Test
    void descriptorForEveryAnnotation() {
        List<String> summary = descriptors.stream()
                .map(d -> d.id().substring(d.id().lastIndexOf('$') + 1) + " " + d.kind() + " out=" + d.outputKey()
                        + " in=" + d.inputKeys() + " sub="
                        + d.subAgents().stream().map(s -> s.substring(s.lastIndexOf('$') + 1)).toList()
                        + guards(d))
                .sorted().toList();
        assertEquals(List.of(
                "Approval#ask HUMAN_IN_THE_LOOP out=approval in=[draft] sub=[]",
                "ApprovedStory#write SEQUENCE out=approval in=[topic] sub=[Writer, Approval]",
                "Bank#transact SUPERVISOR out=result in=[request] sub=[Teller]",
                "Batch#translateAll PARALLEL_MAPPER out=translations in=[items] sub=[Translator]",
                "Cashier#pay AI_AGENT out=cash in=[amount, customer] sub=[] optional=true async=true model=fast"
                        + " tools=[balance_of, debit, audit] system=You are a careful cashier. user=Pay {{amount}} EUR to {{customer}}",
                "DbSearch#searchDb AI_AGENT out=db in=[topic] sub=[]",
                "DraftPlanner#plan PLANNER out=draft in=[topic] sub=[Writer]",
                "Editor#edit AI_AGENT out=draft in=[draft] sub=[]",
                "Expert#answer AI_AGENT out=answer in=[topic] sub=[]",
                "Generalist#answer AI_AGENT out=answer in=[topic] sub=[]",
                "Polisher#polish LOOP out=draft in=[draft] sub=[Scorer, Editor] exit=goodEnough/3/true output=finalDraft",
                "Repeater#repeat LOOP out=draft in=[draft] sub=[Editor] exit=null/10/false",
                "ResearchFan#research PARALLEL out=research in=[topic] sub=[WebSearch, DbSearch]",
                "Reviewer#review AI_AGENT out=review in=[draft] sub=[]",
                "Router#route CONDITIONAL out=answer in=[topic] sub=[Expert, Generalist] when=[Expert:technical, Generalist:general]",
                "Scorer#score AI_AGENT out=Score in=[draft] sub=[]",
                "Shouter#shout NON_AI_AGENT out=shout in=[draft] sub=[]",
                "StoryFlow#write SEQUENCE out=review in=[topic] sub=[Writer, Reviewer]",
                "Teller#move AI_AGENT out=receipt in=[amount] sub=[]",
                "Translator#translate AI_AGENT out=translation in=[item] sub=[]",
                "WebSearch#searchWeb AI_AGENT out=web in=[topic] sub=[]",
                "Writer#write AI_AGENT out=draft in=[topic] sub=[]"),
                summary);
    }
}
