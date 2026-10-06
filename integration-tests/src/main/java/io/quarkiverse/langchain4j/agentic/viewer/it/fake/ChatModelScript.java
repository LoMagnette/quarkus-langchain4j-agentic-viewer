package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

/**
 * Contributes scripted replies to the application's {@link ScriptedChatModel}.
 * Implement it as a CDI bean next to the agents it scripts (samples in main, test agents in test).
 * Rules of different scripts must not match the same requests: match on the agent's own prompt text, and always
 * combine {@link ScriptedChatModel#lastMessageIsToolResult(String)} with a matcher identifying the agent, since
 * tool names are not unique across samples.
 */
public interface ChatModelScript {

    void contribute(ScriptedChatModel.Builder script);
}
