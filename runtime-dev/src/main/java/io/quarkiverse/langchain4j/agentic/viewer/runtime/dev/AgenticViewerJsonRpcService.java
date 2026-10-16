package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

/**
 * JSON-RPC service of the Agentic Viewer Dev UI (method names: {@code quarkus-langchain4j-agentic-viewer_<method>}).
 * Listing systems, topologies and input schemas comes with T2.1.
 */
public class AgenticViewerJsonRpcService {

    /**
     * @return the number of agentic systems known to the running application
     */
    public int getSystemCount() {
        return AgenticViewerRecorder.getSystems().size();
    }
}
