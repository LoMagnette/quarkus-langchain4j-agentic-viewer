package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Where a remote agent runs, as far as it is known before running.
 * <ul>
 * <li>{@link NodeKind#A2A_CLIENT_AGENT}: {@code a2aServerUrl} or {@code serverUrlSupplier}, and {@code tenant}.</li>
 * <li>{@link NodeKind#MCP_CLIENT_AGENT}: {@code toolName} and {@code clientSupplier}.</li>
 * </ul>
 * Fields that do not apply, or are not set, are null.
 *
 * @param a2aServerUrl URL of the A2A server given in the annotation
 * @param serverUrlSupplier {@code Class#method} of the {@code @A2AServerUrlSupplier} method when the URL is computed
 * @param tenant A2A tenant
 * @param toolName name of the MCP tool the agent calls
 * @param clientSupplier {@code Class#method} of the {@code @McpClientSupplier} method creating the MCP client
 */
public record RemoteAgentDetails(String a2aServerUrl, String serverUrlSupplier, String tenant, String toolName,
        String clientSupplier) {
}
