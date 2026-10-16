package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;

/**
 * One run of an agentic system and the events it emitted so far.
 */
public final class Run {

    private final String id;
    private final String systemId;
    private final Map<String, Object> inputs;
    private final long startedAt;
    private final List<ExecutionEvent> events = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Run> completion = new CompletableFuture<>();
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Object output;

    Run(String id, String systemId, Map<String, Object> inputs, long startedAt) {
        this.id = id;
        this.systemId = systemId;
        this.inputs = inputs;
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

    /** @return start time in milliseconds since the epoch */
    public long startedAt() {
        return startedAt;
    }

    public RunStatus status() {
        return status;
    }

    /** @return the result of a succeeded run, null otherwise */
    public Object output() {
        return output;
    }

    /** @return the events emitted so far, in order */
    public List<ExecutionEvent> events() {
        return List.copyOf(events);
    }

    /** @return completes with this run when it ends, whatever the outcome */
    public CompletableFuture<Run> completion() {
        return completion;
    }

    void add(ExecutionEvent event) {
        events.add(event);
    }

    void end(RunStatus endStatus, Object result) {
        output = result;
        status = endStatus;
        completion.complete(this);
    }
}
