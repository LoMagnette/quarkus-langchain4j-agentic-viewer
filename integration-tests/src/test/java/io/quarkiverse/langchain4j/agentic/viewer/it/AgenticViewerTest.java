package io.quarkiverse.langchain4j.agentic.viewer.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the Agentic Viewer extension.
 */
@QuarkusTest
public class AgenticViewerTest {

    @Test
    void testApplicationStarts() {
        // Test that the application starts correctly with the extension
        given()
                .when().get("/test")
                .then()
                .statusCode(200)
                .body(is("Agentic Viewer Test"));
    }
}
