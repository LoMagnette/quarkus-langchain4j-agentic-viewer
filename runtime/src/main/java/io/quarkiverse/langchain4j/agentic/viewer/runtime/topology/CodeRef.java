package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * A piece of application code behind a topology node, for the Dev UI to open in the IDE.
 *
 * @param role what the code is for the node
 * @param className fully qualified name of the class declaring it (or the class a prompt resource belongs to)
 * @param member method name, null for a class or a resource
 * @param resource classpath resource path of a prompt ({@code fromResource}), null otherwise
 * @param line line number when known (a builder call site), null otherwise
 */
public record CodeRef(Role role, String className, String member, String resource, Integer line) {

    /** What a piece of code is for its node. */
    public enum Role {
        /** the agent method (or the agent class of a builder system) */
        AGENT,
        /** the {@code @Output} method of a workflow */
        OUTPUT,
        /** the {@code @PlannerSupplier} method of a planner */
        PLANNER_SUPPLIER,
        /** the {@code @ExitCondition} method of a loop */
        EXIT_CONDITION,
        /** an {@code @ActivationCondition} method of a conditional */
        ACTIVATION_CONDITION,
        /** the {@code @SystemMessage(fromResource)} prompt */
        SYSTEM_MESSAGE,
        /** the {@code @UserMessage(fromResource)} prompt */
        USER_MESSAGE,
        /** where a builder system is built */
        CALL_SITE,
        /** the {@code @ChatModelSupplier} method of an agent */
        CHAT_MODEL_SUPPLIER,
        /** the {@code @StreamingChatModelSupplier} method of an agent */
        STREAMING_CHAT_MODEL_SUPPLIER,
        /** the {@code @ChatMemorySupplier} method of an agent */
        CHAT_MEMORY_SUPPLIER,
        /** the {@code @ChatMemoryProviderSupplier} method of an agent */
        CHAT_MEMORY_PROVIDER_SUPPLIER,
        /** the {@code @ContentRetrieverSupplier} method of an agent */
        CONTENT_RETRIEVER_SUPPLIER,
        /** the {@code @RetrievalAugmentorSupplier} method of an agent */
        RETRIEVAL_AUGMENTOR_SUPPLIER,
        /** the {@code @SystemMessageProviderSupplier} method of an agent */
        SYSTEM_MESSAGE_PROVIDER,
        /** the {@code @UserMessageProviderSupplier} method of an agent */
        USER_MESSAGE_PROVIDER,
        /** the {@code @AgentListenerSupplier} method of an agent */
        AGENT_LISTENER_SUPPLIER,
        /** the {@code @BeforeCall} method of an agent */
        BEFORE_CALL
    }

    public static CodeRef method(Role role, String className, String member) {
        return new CodeRef(role, className, member, null, null);
    }
}
