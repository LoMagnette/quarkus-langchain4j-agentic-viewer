package io.quarkiverse.langchain4j.agentic.viewer.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * The packaged production app (JVM, or native with -Dquarkus.native.enabled) starts with the extension and runs its
 * agentic systems: no build step of the viewer needs the dev-only artifact there (the @QuarkusTest class path has it,
 * so only the packaged app can tell).
 */
@QuarkusIntegrationTest
class ProdStartupIT {

    @Test
    void theProductionAppStarts() {
        given().get("/test").then().statusCode(200).body(equalTo("Agentic Viewer Test"));
    }

    /**
     * Not in native: quarkus-langchain4j-agentic (1.14.2) does not register the agent interfaces for reflection there,
     * langchain4j then finds no agent method ("No agent method found in class"), with or without the viewer.
     */
    @Test
    @DisabledOnIntegrationTest(forArtifactTypes = DisabledOnIntegrationTest.ArtifactType.NATIVE_BINARY)
    void theProductionAppRunsADeclarativeSystem() {
        given().queryParam("topic", "quantum computing").get("/test/research")
                .then().statusCode(200).body(equalTo("Web: 3 articles about quantum computing\n"
                        + "Database: 2 records about quantum computing\n"
                        + "Archives: 1 old report about quantum computing"));
    }

    /** Not in native either: built at runtime with the AgenticServices builders (reflection and proxies). */
    @Test
    @DisabledOnIntegrationTest(forArtifactTypes = DisabledOnIntegrationTest.ArtifactType.NATIVE_BINARY)
    void theProductionAppRunsBuilderSystems() {
        given().queryParam("request", "I hurt my leg while running").get("/builder/expert")
                .then().statusCode(200).body(equalTo("Rest, and see a doctor if it gets worse."));
        given().queryParam("topic", "quantum computing").get("/builder/research")
                .then().statusCode(200)
                .body(equalTo("Web: 3 articles about quantum computing\nDatabase: 2 records about quantum computing"));
    }
}
