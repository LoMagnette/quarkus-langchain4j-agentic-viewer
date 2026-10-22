package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * One run of an agentic system and the events it emitted so far.
 */
public final class Run {

    private final String id;
    private final String systemId;
    private final RunOrigin origin;
    /** the series it belongs to, null for none */
    private final String seriesId;
    private final Map<String, Object> inputs;
    private final long startedAt;
    /** Appended on the event thread, read from any thread: guarded by itself (no copy per event). */
    private final List<ExecutionEvent> events = new ArrayList<>();
    private final CompletableFuture<Run> completion = new CompletableFuture<>();
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Object output;
    private volatile boolean ended;
    /** Whether its RunFinished or RunFailed was recorded; only touched on the event thread of the run manager. */
    private boolean terminated;
    private volatile Long durationMillis;
    /** set by whoever ends the run first: its result, its failure or a cancellation */
    private final AtomicBoolean ending = new AtomicBoolean();
    private volatile Future<?> task;
    /** Live subscribers; only touched on the event thread of the run manager. */
    private final List<MultiEmitter<? super ExecutionEvent>> subscribers = new ArrayList<>();

    Run(String id, String systemId, RunOrigin origin, Map<String, Object> inputs, long startedAt) {
        this(id, systemId, origin, null, inputs, startedAt);
    }

    Run(String id, String systemId, RunOrigin origin, String seriesId, Map<String, Object> inputs, long startedAt) {
        this.id = id;
        this.systemId = systemId;
        this.origin = origin;
        this.seriesId = seriesId;
        this.inputs = inputs;
        this.startedAt = startedAt;
    }

    public String id() {
        return id;
    }

    public String systemId() {
        return systemId;
    }

    /** @return who started the run: the Dev UI, or the application (observed only) */
    public RunOrigin origin() {
        return origin;
    }

    /** @return the series the run belongs to (Agentic Test Runs), null for none */
    public String seriesId() {
        return seriesId;
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
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /** @return completes with this run when it ends, whatever the outcome */
    public CompletableFuture<Run> completion() {
        return completion;
    }

    /** @return the duration of an ended run, null while running */
    public Long durationMillis() {
        return durationMillis;
    }

    /** @return true for the first caller only: the one that ends the run */
    boolean claimEnd(long duration) {
        if (ending.compareAndSet(false, true)) {
            durationMillis = duration;
            return true;
        }
        return false;
    }

    /** @return whether the end of the run was claimed (its terminal event may not be delivered yet) */
    boolean endClaimed() {
        return ending.get();
    }

    void task(Future<?> future) {
        task = future;
    }

    Future<?> task() {
        return task;
    }

    /** @return whether the run ended (all its events were delivered) */
    public boolean ended() {
        return ended;
    }

    /** @return whether the terminal event of the run was recorded: nothing is recorded after it */
    boolean terminated() {
        return terminated;
    }

    void add(ExecutionEvent event) {
        terminated |= event instanceof RunFinished || event instanceof RunFailed;
        synchronized (events) {
            events.add(event);
        }
        subscribers.forEach(subscriber -> subscriber.emit(event));
    }

    /** Replays the events emitted so far, then follows the run (or completes at once if it ended). */
    void subscribe(MultiEmitter<? super ExecutionEvent> subscriber) {
        events().forEach(subscriber::emit);
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
