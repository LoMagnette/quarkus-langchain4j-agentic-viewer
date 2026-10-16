package io.quarkiverse.langchain4j.agentic.viewer.test.patterns;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals;

/**
 * Behaviour of langchain4j-agentic-patterns the adapters rely on, beyond the members LangChain4jInternals checks.
 */
class PatternInternalsTest {

    @Test
    void plannersExposeTheirInvocationCounter() {
        assertTrue(new P2PPlanner().executionState().containsKey(LangChain4jInternals.INVOCATION_COUNTER));
        assertTrue(new BlackboardPlanner().executionState().containsKey(LangChain4jInternals.INVOCATION_COUNTER));
    }
}
