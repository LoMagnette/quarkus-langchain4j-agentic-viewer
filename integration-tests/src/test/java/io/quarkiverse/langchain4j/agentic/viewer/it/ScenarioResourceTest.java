package io.quarkiverse.langchain4j.agentic.viewer.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.it.ScenarioResource.Scenario;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;

/**
 * Every sample runs over HTTP with the defaults of its endpoint, with the results of the sample tests.
 */
@QuarkusTest
class ScenarioResourceTest {

    @Inject
    BankSupervisor.BankTool bankTool;

    @BeforeEach
    void reset() {
        bankTool.reset();
        HumanInput.reset();
    }

    @Test
    void theIndexListsEveryScenario() {
        String index = given().get("/scenarios").then().statusCode(200).extract().asString();
        ScenarioResource.SCENARIOS.forEach(
                scenario -> assertThat(index, containsString("GET " + scenario.path() + "?" + scenario.query())));
        assertThat(index, containsString("/scenarios/patterns"));
    }

    @Test
    void everyScenarioRunsWithTheQueryOfTheIndex() {
        for (Scenario scenario : ScenarioResource.SCENARIOS) {
            RequestSpecification request = given();
            query(scenario.query()).forEach((name, values) -> request.queryParam(name, values));
            request.get(scenario.path()).then().statusCode(200);
            bankTool.reset();
        }
    }

    @Test
    void declarativeSamples() {
        expect("/scenarios/story-writer", "Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.");
        expect("/scenarios/research-gatherer", "Web: 3 articles about quantum computing\n"
                + "Database: 2 records about quantum computing\n"
                + "Archives: 1 old report about quantum computing");
        expect("/scenarios/phrase-translator", "bonjour\nmerci\nbonne nuit");
        expect("/scenarios/story-creator", "A story about a lighthouse (draft 4)");
        expect("/scenarios/expert-router", "Rest, and see a doctor if it gets worse.");
        expect("/scenarios/bank-supervisor", "Transferred 100 EUR (115.0 USD) from Mario to Georgios.");
        expect("/scenarios/draft-reviewer", "A polished draft about solar panels");
        expect("/scenarios/trip-planner", "Day 1, 2 and 3 in Lisbon");
        expect("/scenarios/resilient-story-writer", "A tiny tale of dragons and wizards.");
        expect("/scenarios/product-launch", "Launch plan of Photon, Campaign (v4)\n"
                + "Pitches: pitch for Belgium | pitch for Japan\n"
                + "Channels: paid media for 20000 + an event for Photon");
    }

    @Test
    void queryParametersReachTheSystems() {
        given().queryParam("request", "My laptop does not start").get("/scenarios/expert-router")
                .then().statusCode(200).body(equalTo("Restart it, then update the drivers."));
        given().queryParam("topic", "pirates").get("/scenarios/resilient-story-writer")
                .then().statusCode(200).body(equalTo("A tiny tale of pirates."));
        given().queryParam("phrase", "hello", "good night").get("/scenarios/phrase-translator")
                .then().statusCode(200).body(equalTo("bonjour\nbonne nuit"));
        given().queryParam("audience", "CONSUMERS").queryParam("budget", 5000).queryParam("market", "Brazil")
                .queryParam("tone", "calm").queryParam("maxWords", 20).get("/scenarios/product-launch")
                .then().statusCode(200).body(endsWith("Channels: social media for Photon"));
    }

    @Test
    void builderAndMixedSamples() {
        expect("/scenarios/builder/story-creator", "A story about a lighthouse (draft 4)");
        expect("/scenarios/builder/draft-reviewer", "A polished draft about solar panels");
        expect("/scenarios/builder/story-writer", "Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.");
        expect("/scenarios/blurbed-story", "A tale you will not forget.");
    }

    private static void expect(String path, String output) {
        given().get(path).then().statusCode(200).body(equalTo(output));
    }

    /** The parameters of an index query, repeated names kept in order. */
    static Map<String, List<String>> query(String query) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            parameters.computeIfAbsent(pair.substring(0, equals), name -> new ArrayList<>())
                    .add(pair.substring(equals + 1));
        }
        return parameters;
    }
}
