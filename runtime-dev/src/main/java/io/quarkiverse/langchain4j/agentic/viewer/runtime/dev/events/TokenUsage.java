package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * Tokens used by an AI agent invocation, null fields when the model does not report them.
 */
public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
}
