package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;

/**
 * Settings of an agent or workflow that change how it behaves, as declared (langchain4j-agentic annotations).
 *
 * @param compensateOnError when a tool of the agent fails, the successful tool calls before it with
 *        {@code @CompensateFor} actions are compensated in reverse order ({@code compensateOnError})
 * @param summarizedContext names of the other agents whose interactions make up this agent's context
 *        ({@code @Agent(summarizedContext)}), empty when none
 * @param maxAgentsInvocations maximum of sub-agent invocations in one execution of a supervisor, null for other kinds
 * @param contextStrategy what context a supervisor gets ({@code SupervisorContextStrategy} name), null for other kinds
 * @param responseStrategy which response a supervisor returns ({@code SupervisorResponseStrategy} name), null for
 *        other kinds
 */
public record AgentSettings(boolean compensateOnError, List<String> summarizedContext, Integer maxAgentsInvocations,
        String contextStrategy, String responseStrategy) {

    /** The defaults of {@code @SupervisorAgent}. */
    public static final int DEFAULT_MAX_AGENTS_INVOCATIONS = 10;
    public static final String DEFAULT_CONTEXT_STRATEGY = "CHAT_MEMORY";
    public static final String DEFAULT_RESPONSE_STRATEGY = "LAST";

    public AgentSettings {
        summarizedContext = summarizedContext == null ? List.of() : List.copyOf(summarizedContext);
    }
}
