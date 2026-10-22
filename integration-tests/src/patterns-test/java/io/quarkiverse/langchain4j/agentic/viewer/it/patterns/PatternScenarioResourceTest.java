package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.it.ScenarioResource.Scenario;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Every pattern sample runs over HTTP with the defaults of its endpoint, with the results of PatternSamplesTest.
 */
@QuarkusTest
class PatternScenarioResourceTest {

    @AfterEach
    void resetHumanInput() {
        HumanInput.reset();
    }

    @Test
    void theIndexListsEveryScenario() {
        String index = given().get("/scenarios/patterns").then().statusCode(200).extract().asString();
        for (Scenario scenario : PatternScenarioResource.SCENARIOS) {
            assertThat(index, containsString("GET " + scenario.path() + "?" + scenario.query()));
        }
    }

    @Test
    void patternSamples() {
        expect("/scenarios/patterns/trip-goal-planner", "Stay at Hotel Central in Lisbon, enjoy a boat tour and a picnic");
        expect("/scenarios/patterns/research-team", "Hypothesis v3 about quantum batteries");
        expect("/scenarios/patterns/incident-responder", "Bound the order cache to 10,000 entries");
        expect("/scenarios/patterns/release-captain", "Version 1.2.1 is out: it fixes the invoice export");
        expect("/scenarios/patterns/design-debate", "Adopt one queue per region");
        expect("/scenarios/patterns/review-panel", "APPROVE");
        expect("/scenarios/patterns/feature-delivery", "Released: Exports now stream (review: APPROVE)");
    }

    private static void expect(String path, String output) {
        given().get(path).then().statusCode(200).body(equalTo(output));
    }
}
