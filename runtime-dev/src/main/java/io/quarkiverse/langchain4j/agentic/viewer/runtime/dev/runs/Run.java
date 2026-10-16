package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.smallrye.mutiny.subscription.MultiEmitter;

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
    private volatile boolean ended;
    /** Live subscribers; only touched on the event thread of the run manager. */
    private final List<MultiEmitter<? super ExecutionEvent>> subscribers = new ArrayList<>();

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

    /** @return whether the run ended (all its events were delivered) */
    public boolean ended() {
        return ended;
    }

    void add(ExecutionEvent event) {
        events.add(event);
        subscribers.forEach(subscriber -> subscriber.emit(event));
    }

    /** Replays the events emitted so far, then follows the run (or completes at once if it ended). */
    void subscribe(MultiEmitter<? super ExecutionEvent> subscriber) {
        events.forEach(subscriber::emit);
        if (ended) {
            subscriber.complete();
        } else {
            subscribers.add(subscriber);
        }
    }

    void unsubscribe(MultiEmitter<? super ExecutionEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    void end(RunStatus endStatus, Object result) {
        output = result;
        status = endStatus;
        ended = true;
        subscribers.forEach(MultiEmitter::complete);
        subscribers.clear();
        completion.complete(this);
    }
}
