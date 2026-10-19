package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The langchain4j-agentic-patterns samples run end to end on the scripted fake model (profile "patterns").
 */
@QuarkusTest
class PatternSamplesTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    TripGoalPlanner tripGoalPlanner;

    @Inject
    ResearchTeam researchTeam;

    @Inject
    IncidentResponder incidentResponder;

    @Inject
    ReleaseCaptain releaseCaptain;

    @Inject
    DesignDebate designDebate;

    @Inject
    ReviewPanel reviewPanel;

    @Inject
    FeatureDelivery featureDelivery;

    @BeforeEach
    void clearRequests() {
        model.clearRequests();
    }

    @AfterEach
    void resetHumanInput() {
        HumanInput.reset();
    }

    @Test
    void goalOrientedPlannerCallsOnlyTheAgentsNeededForTheGoal() {
        assertEquals("Stay at Hotel Central in Lisbon, enjoy a boat tour and a picnic", tripGoalPlanner.plan("Lisbon", "medium"));

        List<String> prompts = prompts();
        assertEquals(4, prompts.size(), "forecast, hotel, activities, itinerary: " + prompts);
        assertEquals(0, prompts.stream().filter(p -> p.startsWith("Review the best restaurant")).count());
        assertEquals(true, prompts.get(prompts.size() - 1).startsWith("Build an itinerary"));
    }

    @Test
    void peerToPeerStopsOnTheExitCondition() {
        assertEquals("Hypothesis v3 about quantum batteries", researchTeam.research("quantum batteries"));
        assertEquals(List.of("search", "formulate", "criticize", "refine", "criticize", "refine", "criticize"),
                prompts().stream().map(PatternSamplesTest::step).toList());
    }

    @Test
    void blackboardResolvesTheConflictMetricsFirst() {
        assertEquals("Bound the order cache to 10,000 entries", incidentResponder.respond("Checkout fails since 10:00"));
        assertEquals(List.of("metrics", "logs", "diagnose", "fix"),
                prompts().stream().map(PatternSamplesTest::step).toList());
    }

    @Test
    void bdiPursuesTheDesiresByPriority() {
        assertEquals("Version 1.2.1 is out: it fixes the invoice export", releaseCaptain.release("Invoice export fails"));
        assertEquals(List.of("Write a patch", "Write release notes", "Announce the release"), prompts().stream()
                .map(p -> p.startsWith("Write a patch") ? "Write a patch" : p.startsWith("Write release notes")
                        ? "Write release notes" : p.startsWith("Announce the release") ? "Announce the release" : p)
                .toList());
    }

    @Test
    void debateRunsItsRoundsThenTheJudge() {
        assertEquals("Adopt one queue per region", designDebate.debate("one queue"));
        List<String> prompts = prompts().stream().map(p -> p.substring(0, p.indexOf(" the proposal"))).toList();
        assertEquals(5, prompts.size(), "two rounds of two debaters, then the judge: " + prompts);
        // the debaters of a round run in parallel
        assertEquals(List.of("Argue against", "Argue for"), prompts.subList(0, 2).stream().sorted().toList());
        assertEquals(List.of("Argue against", "Argue for"), prompts.subList(2, 4).stream().sorted().toList());
        assertEquals("Decide on", prompts.get(4));
    }

    @Test
    void votingTakesTheMajority() {
        assertEquals("APPROVE", reviewPanel.review("Cache the invoices"));
        assertEquals(3, prompts().size());
    }

    @Test
    void featureDeliveryNestsThePlannersInTheWorkflows() {
        assertEquals("Released: Exports now stream (review: APPROVE)", featureDelivery.deliver("CSV export", "acme/billing"));

        List<String> steps = prompts().stream().map(PatternSamplesTest::deliveryStep).toList();
        // discovery in parallel, the scout asking the model twice (tool call, then the answer from its result)
        assertEquals(List.of("interview", "scout", "scout"), steps.subList(0, 3).stream().sorted().toList(), steps::toString);
        // GOAP: only what the spec needs (no estimate, no sketch); votes in parallel, rejected then approved; BDI
        assertEquals(List.of("requirements", "risks", "spec", "implement"), steps.subList(3, 7), steps::toString);
        assertEquals(List.of("performance", "readability", "security"), steps.subList(7, 10).stream().sorted().toList());
        assertEquals("refactor", steps.get(10));
        assertEquals(List.of("performance", "readability", "security"), steps.subList(11, 14).stream().sorted().toList());
        assertEquals(List.of("rollout", "changelog", "announce"), steps.subList(14, steps.size()), steps::toString);
        assertEquals(List.of("Ship Streaming export (v2)?"), HumanInput.prompts(), "the product owner signs off the approved code");
    }

    @Test
    void featureDeliveryIsPostponedWithoutTheProductOwnersYes() {
        HumanInput.answerWith(prompt -> "No, wait for the audit");
        assertEquals("Postponed: No, wait for the audit (review: APPROVE)", featureDelivery.deliver("CSV export", "acme/billing"));
        List<String> steps = prompts().stream().map(PatternSamplesTest::deliveryStep).toList();
        assertEquals("postpone", steps.get(steps.size() - 1), steps::toString);
        assertEquals(false, steps.contains("rollout"), "the release train does not run: " + steps);
    }

    private static String deliveryStep(String prompt) {
        for (String[] step : new String[][] { { "Interview users", "interview" }, { "Scout the code base", "scout" },
                { "Write the requirements", "requirements" }, { "Assess the delivery risks", "risks" },
                { "Estimate the effort", "estimate" }, { "Sketch the screens", "sketch" }, { "Write the spec", "spec" },
                { "Implement this spec", "implement" }, { "readability of the code", "readability" },
                { "security of the code", "security" }, { "performance of the code", "performance" },
                { "Refactor the code", "refactor" }, { "Plan a staged rollout", "rollout" },
                { "Write the changelog", "changelog" }, { "Announce the feature release", "announce" },
                { "is postponed", "postpone" } }) {
            if (prompt.contains(step[0])) {
                return step[1];
            }
        }
        return prompt;
    }

    private List<String> prompts() {
        return model.requests().stream()
                .map(request -> ScriptedChatModel.lastUserMessage(request).lines().findFirst().orElse(""))
                .toList();
    }

    private static String step(String prompt) {
        if (prompt.startsWith("Find papers")) {
            return "search";
        }
        if (prompt.startsWith("Formulate")) {
            return "formulate";
        }
        if (prompt.startsWith("Criticize")) {
            return "criticize";
        }
        if (prompt.startsWith("Refine")) {
            return "refine";
        }
        if (prompt.startsWith("Analyze the metrics")) {
            return "metrics";
        }
        if (prompt.startsWith("Analyze the logs")) {
            return "logs";
        }
        if (prompt.startsWith("Diagnose")) {
            return "diagnose";
        }
        if (prompt.startsWith("Propose a fix")) {
            return "fix";
        }
        return prompt;
    }
}
