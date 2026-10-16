package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapter;

/**
 * Pattern adapters are registered only for the pattern planners on the class path (T4.1).
 */
class PatternAdaptersSelectionTest {

    private static final String PATTERNS = "io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.";

    @Test
    void noneWithoutTheModule() {
        assertEquals(List.of(), AgenticViewerProcessor.patternAdapters(className -> false));
    }

    @Test
    void allWithTheModule() {
        assertEquals(List.of(PATTERNS + "BlackboardAdapter", PATTERNS + "GoapAdapter", PATTERNS + "P2PAdapter"),
                AgenticViewerProcessor.patternAdapters(className -> className.startsWith("dev.langchain4j.agentic.patterns.")));
    }

    @Test
    void onlyThePlannersPresent() {
        assertEquals(List.of(PATTERNS + "GoapAdapter"), AgenticViewerProcessor.patternAdapters(
                className -> className.equals("dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner")));
    }

    @Test
    void adapterNamesAreAdapters() throws Exception {
        // loaded without being linked: their pattern planner classes are not on this class path
        for (String adapter : AgenticViewerProcessor.PATTERN_ADAPTERS.values()) {
            Class<?> type = Class.forName(adapter, false, getClass().getClassLoader());
            assertTrue(PatternAdapter.class.isAssignableFrom(type), adapter);
        }
    }
}
