package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A series: one system run several times with the same inputs, one run after the other (the Agentic Test Runs page).
 * Its runs are Dev UI runs that carry its id; they are kept with it, out of the system's run history.
 */
public final class Series {

    private final String id;
    private final String systemId;
    private final Map<String, Object> inputs;
    private final int count;
    private final long startedAt;
    private final List<String> runIds = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Series> completion = new CompletableFuture<>();
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile boolean cancelled;
    private volatile Long durationMillis;
    private volatile String error;

    Series(String id, String systemId, Map<String, Object> inputs, int count, long startedAt) {
        this.id = id;
        this.systemId = systemId;
        this.inputs = inputs;
        this.count = count;
        this.startedAt = startedAt;
    }

    public String id() {
        return id;
    }

    public String systemId() {
        return systemId;
    }

    public Map<String, Object> inputs() {
        return inputs;
    }

    /** @return the number of runs asked for */
    public int count() {
        return count;
    }

    public long startedAt() {
        return startedAt;
    }

    /** @return the ids of the runs started so far, in order */
    public List<String> runIds() {
        return List.copyOf(runIds);
    }

    /**
     * @return RUNNING, then SUCCEEDED once every run ended (whatever their outcome), CANCELLED when stopped, FAILED
     *         when a run could not start
     */
    public RunStatus status() {
        return status;
    }

    public Long durationMillis() {
        return durationMillis;
    }

    /** @return why the series could not go on (FAILED), null otherwise */
    public String error() {
        return error;
    }

    /** @return completes with this series when it ends */
    public CompletableFuture<Series> completion() {
        return completion;
    }

    boolean cancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
    }

    void started(String runId) {
        runIds.add(runId);
    }

    /** @return the run started last, null before the first */
    String currentRunId() {
        List<String> ids = runIds();
        return ids.isEmpty() ? null : ids.get(ids.size() - 1);
    }

    void end(RunStatus endStatus, String failure) {
        error = failure;
        durationMillis = Math.max(0, System.currentTimeMillis() - startedAt);
        status = endStatus;
        completion.complete(this);
    }
}
