package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals.Dependency;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals.Library;

/**
 * The internal parts of langchain4j-agentic (and of the patterns module when present, CI's second configuration) the
 * live view relies on are all there: fails on an upgrade that changes one of them, naming what it breaks.
 */
class LangChain4jInternalsTest {

    @Test
    void everyInternalPartIsThere() {
        assertEquals(List.of(), LangChain4jInternals.problems(getClass().getClassLoader()));
    }

    @Test
    void aMissingPartIsReported() {
        List<Dependency> changed = List.of(
                new Dependency(Library.AGENTIC, LangChain4jInternals.PLANNER_HANDLER, "renamedField", null, "planner decisions"),
                new Dependency(Library.AGENTIC, "dev.langchain4j.agentic.internal.Gone", null, null, "runs"),
                new Dependency(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentExecutor", "agent",
                        List.of("java.lang.String"), "human-in-the-loop answers"));
        assertEquals(List.of(
                LangChain4jInternals.PLANNER_HANDLER + ".renamedField (planner decisions): field not found",
                "dev.langchain4j.agentic.internal.Gone (runs): class not found",
                "dev.langchain4j.agentic.internal.AgentExecutor.agent[java.lang.String] (human-in-the-loop answers): method not found"),
                LangChain4jInternals.problems(changed, getClass().getClassLoader()));
    }
}
