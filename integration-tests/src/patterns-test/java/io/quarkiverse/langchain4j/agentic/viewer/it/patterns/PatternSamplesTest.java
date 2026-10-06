package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
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

    @BeforeEach
    void clearRequests() {
        model.clearRequests();
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
