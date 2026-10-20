package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.config.AgenticViewerRuntimeConfig;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderSystems;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorHandled;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PayloadTruncator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Starts runs of agentic systems on its own worker threads and reports their lifecycle as events.
 * Constructible without CDI from a system lookup and an invoker, so tests can drive real or fake systems.
 */
@ApplicationScoped
public class RunManager {

    private static final Logger LOG = Logger.getLogger(RunManager.class);
    private static final AtomicInteger THREADS = new AtomicInteger();
    /** The run executed by the current run thread, for listeners that need to know which run a callback belongs to. */
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();

    private final Function<String, Optional<AgenticSystem>> systems;
    private final RunInvoker invoker;
    private final ExecutorService executor;
    /** One thread delivers every event in order, so emitting never waits for the listeners. */
    private final ExecutorService dispatcher;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    /** Run ids per system, oldest first; guarded by itself. */
    private final Map<String, Deque<String>> history = new HashMap<>();
    private final int historySize;
    /** Run threads that may be busy at once: a permit is taken before a run exists, given back when its thread returns. */
    private final Semaphore runThreads;
    private final int maxConcurrentRuns;
    private final PayloadTruncator truncator;
    private final List<Consumer<ExecutionEvent>> listeners = new CopyOnWriteArrayList<>();
    /** Human-in-the-loop steps waiting for an answer, per run id, oldest first. */
    private final Map<String, Deque<PendingInput>> pendingInputs = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private volatile boolean shutdown;

    /** A step waiting for an answer of a given type. */
    private record PendingInput(Type valueType, CompletableFuture<Object> answer) {
    }

    public static final int DEFAULT_HISTORY_SIZE = 20;
    public static final int DEFAULT_MAX_CONCURRENT_RUNS = 16;
    private static final long EVENT_THREAD_IDLE_SECONDS = 5;

    @Inject
    public RunManager(AgenticSystemRegistry registry, BuilderSystems builderSystems, AgenticViewerRuntimeConfig config) {
        this(registry::find, new CdiRunInvoker(builderSystems::root), config.maxPayloadSize(), config.historySize(),
                config.maxConcurrentRuns());
    }

    public RunManager(Function<String, Optional<AgenticSystem>> systems, RunInvoker invoker) {
        this(systems, invoker, PayloadTruncator.DEFAULT_MAX_SIZE, DEFAULT_HISTORY_SIZE);
    }

    public RunManager(Function<String, Optional<AgenticSystem>> systems, RunInvoker invoker, int maxPayloadSize,
            int historySize) {
        this(systems, invoker, maxPayloadSize, historySize, DEFAULT_MAX_CONCURRENT_RUNS);
    }

    public RunManager(Function<String, Optional<AgenticSystem>> systems, RunInvoker invoker, int maxPayloadSize,
            int historySize, int maxConcurrentRuns) {
        this.systems = systems;
        this.maxConcurrentRuns = Math.max(1, maxConcurrentRuns);
        this.runThreads = new Semaphore(this.maxConcurrentRuns);
        this.invoker = invoker;
        this.historySize = Math.max(1, historySize);
        this.truncator = new PayloadTruncator(maxPayloadSize);
        // run threads load the application classes: they keep the class loader of whoever created the manager
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        this.executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "agentic-viewer-run-" + THREADS.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(classLoader);
            return thread;
        });
        // one thread at most (events in order), which ends when idle: a manager never shut down keeps no thread
        ThreadPoolExecutor events = new ThreadPoolExecutor(1, 1, EVENT_THREAD_IDLE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "agentic-viewer-events-" + THREADS.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setContextClassLoader(classLoader);
                    return thread;
                });
        events.allowCoreThreadTimeOut(true);
        this.dispatcher = events;
    }

    /**
     * Starts a run and returns at once; the run executes on a worker thread.
     *
     * @param inputs input values by name
     * @return the run id
     * @throws IllegalArgumentException for an unknown system
     * @throws IllegalStateException when {@code max-concurrent-runs} runs are running (no run is created)
     */
    public String startRun(String systemId, Map<String, Object> inputs) {
        AgenticSystem system = systems.apply(systemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
        if (!runThreads.tryAcquire()) {
            throw new IllegalStateException("Too many runs at once: " + maxConcurrentRuns + " are running (cancelled ones "
                    + "count until their thread returns); start it again once one ended, or raise "
                    + "quarkus.langchain4j.agentic.dev-ui.max-concurrent-runs");
        }
        // a copy: the run executes and its events are written later, the caller's map may change meanwhile (null values
        // stay: an optional input left empty)
        Map<String, Object> runInputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        Run run = new Run(UUID.randomUUID().toString(), systemId, runInputs, System.currentTimeMillis());
        runs.put(run.id(), run);
        remember(run);
        emit(run, new RunStarted(run.id(), run.startedAt(), systemId, runInputs));
        // known to the run before it starts: a cancel at any time finds it
        FutureTask<Void> task = new FutureTask<>(() -> execute(run, system), null);
        run.task(task);
        try {
            // the permit is given back when the thread is done with the run, also after a cancel (even before it ran)
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    runThreads.release();
                }
            });
        } catch (RuntimeException e) {
            runThreads.release();
            throw e;
        }
        return run.id();
    }

    /** Adds the run to its system's history. */
    private void remember(Run run) {
        synchronized (history) {
            history.computeIfAbsent(run.systemId(), id -> new ArrayDeque<>()).addLast(run.id());
            prune(run.systemId(), null);
        }
    }

    /**
     * Keeps the newest {@code historySize} ended runs of a system and every run still running (they are only dropped
     * once ended, oldest first): when a run starts and when one ends.
     *
     * @param ending a run ending now, counted as ended
     */
    private void prune(String systemId, Run ending) {
        synchronized (history) {
            Deque<String> ids = history.get(systemId);
            if (ids == null) {
                return;
            }
            long ended = ids.stream().map(runs::get).filter(r -> r == null || r == ending || r.ended()).count();
            for (var it = ids.iterator(); ended > historySize && it.hasNext();) {
                Run old = runs.get(it.next());
                if (old == null || old == ending || old.ended()) {
                    it.remove();
                    ended--;
                    if (old != null) {
                        runs.remove(old.id());
                    }
                }
            }
        }
    }

    /**
     * @return the runs of a system kept in the history, newest first
     */
    public List<RunSummary> listRuns(String systemId) {
        List<String> ids;
        synchronized (history) {
            ids = new ArrayList<>(history.getOrDefault(systemId, new ArrayDeque<>()));
        }
        Collections.reverse(ids);
        return ids.stream().map(runs::get).filter(Objects::nonNull).map(RunSummary::of).toList();
    }

    /**
     * @return the run with all its events, empty for an unknown (or evicted) run
     */
    public Optional<RunTrace> trace(String runId) {
        return run(runId).map(run -> new RunTrace(RunSummary.of(run), run.events()));
    }

    /**
     * Cancels a running run, best effort: it ends at once as CANCELLED and its thread is interrupted, but a call that
     * ignores interruption (e.g. a blocking model call) may keep going; its result is then ignored.
     *
     * @return whether the run was running and is now cancelled
     */
    public boolean cancel(String runId) {
        Run run = run(runId).orElse(null);
        if (run == null || !run.claimEnd(elapsedSince(run))) {
            return false;
        }
        emit(run, new RunFailed(run.id(), System.currentTimeMillis(),
                ErrorInfo.of(new CancellationException("Run cancelled")), run.durationMillis()));
        end(run, RunStatus.CANCELLED, null);
        failPendingInputs(run.id(), "Run cancelled");
        Future<?> task = run.task();
        if (task != null) {
            task.cancel(true);
        }
        return true;
    }

    private static long elapsedSince(Run run) {
        return Math.max(0, System.currentTimeMillis() - run.startedAt());
    }

    private void execute(Run run, AgenticSystem system) {
        long start = System.nanoTime();
        CURRENT.set(run);
        try {
            Object output = invoker.invoke(system, run.inputs());
            if (run.claimEnd(elapsedMillis(start))) { // not cancelled meanwhile
                emit(run, new RunFinished(run.id(), System.currentTimeMillis(), output, run.durationMillis()));
                end(run, RunStatus.SUCCEEDED, output);
            }
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException e && e.getCause() != null ? e.getCause() : error;
            if (run.claimEnd(elapsedMillis(start))) {
                emit(run, new RunFailed(run.id(), System.currentTimeMillis(), ErrorInfo.of(cause), run.durationMillis()));
                end(run, RunStatus.FAILED, null);
            }
        } finally {
            CURRENT.remove();
            failPendingInputs(run.id(), "Run ended"); // e.g. a step of a branch still waiting after a failure
        }
    }

    /**
     * Reports that a human-in-the-loop step of a run waits, then blocks until {@link #provideHumanInput} answers it or
     * the run ends.
     *
     * @param valueType type the answer is converted to
     * @return the answer
     * @throws CancellationException when the run ends (or the thread is interrupted) first
     */
    Object awaitHumanInput(Run run, AwaitingHumanInput waiting, Type valueType) {
        PendingInput pending = new PendingInput(valueType, new CompletableFuture<>());
        Deque<PendingInput> runInputs = pendingInputs.computeIfAbsent(run.id(), id -> new ConcurrentLinkedDeque<>());
        runInputs.addLast(pending);
        try {
            // the claim, not ended(): ending is delivered later, after failPendingInputs may have run
            if (run.endClaimed()) {
                throw new CancellationException("Run ended");
            }
            emit(run, waiting);
            return pending.answer().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for human input");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : new IllegalStateException(e.getCause());
        } finally {
            runInputs.remove(pending);
            pendingInputs.computeIfPresent(run.id(), (id, inputs) -> inputs.isEmpty() ? null : inputs);
        }
    }

    /**
     * Answers the oldest human-in-the-loop step of a run waiting for one.
     *
     * @param value the answer as typed: taken as is for a String step, parsed as JSON otherwise
     * @return true if a step was waiting and got the answer, false if none was waiting
     * @throws IllegalArgumentException if the value does not fit the step's type (the step keeps waiting)
     */
    public boolean provideHumanInput(String runId, String value) {
        Deque<PendingInput> runInputs = runId == null ? null : pendingInputs.get(runId);
        if (runInputs == null) {
            return false;
        }
        for (PendingInput pending : runInputs) {
            if (!pending.answer().isDone()) {
                Object answer = convert(value, pending.valueType());
                if (pending.answer().complete(answer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object convert(String value, Type type) {
        if (type == null || type == String.class || type == Object.class || type == CharSequence.class) {
            return value;
        }
        try {
            return MAPPER.readValue(value == null ? "null" : value, MAPPER.constructType(type));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Expected " + type.getTypeName() + " as JSON: " + e.getOriginalMessage());
        }
    }

    private void failPendingInputs(String runId, String reason) {
        Deque<PendingInput> runInputs = pendingInputs.get(runId);
        if (runInputs != null) {
            runInputs.forEach(pending -> pending.answer().completeExceptionally(new CancellationException(reason)));
        }
    }

    /** Ends the run once every event emitted before is delivered, then bounds its system's history again. */
    private void end(Run run, RunStatus status, Object output) {
        dispatcher.execute(() -> {
            prune(run.systemId(), run); // before the run completes: whoever waits on it sees the bounded history
            run.end(status, output);
        });
    }

    /**
     * @return the run executed by the current thread, if it is a run thread
     */
    static Optional<Run> currentRun() {
        return Optional.ofNullable(CURRENT.get());
    }

    Optional<AgenticSystem> system(String systemId) {
        return systems.apply(systemId);
    }

    public Optional<Run> run(String runId) {
        return Optional.ofNullable(runId).map(runs::get);
    }

    /**
     * The events of a run: those emitted so far, then the live ones; completes when the run ends. A subscriber joining
     * after the end gets the whole run. Set up on the event thread, like the delivery of events, so nothing is missed
     * or repeated; each subscriber receives on a worker thread, so a slow one never holds the event thread.
     *
     * @throws IllegalArgumentException (as a failure of the stream) for an unknown run
     */
    public Multi<ExecutionEvent> stream(String runId) {
        Optional<Run> found = run(runId);
        if (found.isEmpty()) {
            return Multi.createFrom().failure(new IllegalArgumentException("Unknown run: " + runId));
        }
        Run run = found.get();
        return Multi.createFrom().<ExecutionEvent> emitter(emitter -> {
            emitter.onTermination(() -> dispatcher.execute(() -> run.unsubscribe(emitter)));
            dispatcher.execute(() -> run.subscribe(emitter));
        }).emitOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Adds a listener receiving every event of every run, in order, on the event thread: a slow listener delays the
     * other listeners, never the runs.
     */
    public void addListener(Consumer<ExecutionEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Queues an event of a run and returns at once: the event thread records it on the run and passes it to the
     * listeners, in emission order.
     */
    public void emit(Run run, ExecutionEvent event) {
        dispatcher.execute(() -> deliver(run, event));
    }

    private void deliver(Run run, ExecutionEvent raw) {
        if (run.ended() || run.terminated()) {
            return; // e.g. callbacks of a cancelled run whose thread kept going
        }
        ExecutionEvent event;
        try {
            event = bounded(raw);
        } catch (RuntimeException e) {
            // never expected (the truncator does not throw), but a run must still end with its terminal event
            LOG.warnf(e, "Agentic viewer: cannot bound the payload of %s", raw.getType());
            if (!(raw instanceof RunFinished finished)) {
                return;
            }
            event = new RunFinished(finished.runId(), finished.timestamp(), null, finished.durationMillis());
        }
        run.add(event);
        for (Consumer<ExecutionEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError && !(e instanceof StackOverflowError)) {
                    throw e;
                }
                LOG.warnf(e, "Agentic viewer event listener failed on %s", event.getType());
            }
        }
    }

    /**
     * The event with JSON-safe, bounded payload values (on the event thread, never on agent threads).
     */
    private ExecutionEvent bounded(ExecutionEvent event) {
        PayloadTruncator t = truncator;
        return switch (event) {
            case RunStarted e -> new RunStarted(e.runId(), e.timestamp(), e.systemId(), t.truncateValues(e.inputs()));
            case RunFinished e -> new RunFinished(e.runId(), e.timestamp(), t.truncate(e.output()).value(), e.durationMillis());
            case AgentStarted e -> new AgentStarted(e.runId(), e.timestamp(), e.nodeId(), e.invocationId(),
                    e.parentInvocationId(), e.agentName(), e.iteration(), e.itemIndex(), t.truncateValues(e.inputs()),
                    e.streaming());
            case AgentFinished e -> new AgentFinished(e.runId(), e.timestamp(), e.nodeId(), e.invocationId(),
                    t.truncate(e.output()).value(), e.tokens(), e.durationMillis());
            case ToolStarted e -> new ToolStarted(e.runId(), e.timestamp(), e.invocationId(), e.toolCallId(), e.toolName(),
                    t.truncateText(e.arguments()));
            case ToolFinished e -> new ToolFinished(e.runId(), e.timestamp(), e.invocationId(), e.toolCallId(),
                    e.toolName(), t.truncateText(e.result()), e.durationMillis());
            case ScopeStateChanged e -> {
                PayloadTruncator.Result value = t.truncate(e.value());
                yield new ScopeStateChanged(e.runId(), e.timestamp(), e.key(), value.value(),
                        e.truncated() || value.truncated(), e.nodeId());
            }
            case ErrorHandled e -> new ErrorHandled(e.runId(), e.timestamp(), e.nodeId(), e.invocationId(),
                    e.agentName(), e.error(), e.outcome(), t.truncate(e.result()).value());
            case AwaitingHumanInput e -> new AwaitingHumanInput(e.runId(), e.timestamp(), e.nodeId(), e.invocationId(),
                    e.prompt(), e.expectedKey(), e.valueType(), t.truncateValues(e.inputs()));
            case PlannerDecision e -> new PlannerDecision(e.runId(), e.timestamp(), e.nodeId(), e.invocationId(),
                    e.selectedNodeIds(), e.done(), t.truncateValues(e.extras()));
            default -> event; // no free-form payload
        };
    }

    /**
     * The application stops or a hot reload restarts it: the runs still running end now, CANCELLED (their streams
     * complete), waiting human-in-the-loop steps are released, the run threads are interrupted and the event thread
     * stops once it delivered what was emitted. Nothing keeps a thread or the old class loader alive.
     */
    @PreDestroy
    public void shutdown() {
        shutdown = true;
        try {
            for (Run run : runs.values()) {
                if (run.claimEnd(elapsedSince(run))) {
                    emit(run, new RunFailed(run.id(), System.currentTimeMillis(),
                            ErrorInfo.of(new CancellationException("Application stopped or reloaded")), run.durationMillis()));
                    end(run, RunStatus.CANCELLED, null);
                }
            }
            pendingInputs.keySet().forEach(runId -> failPendingInputs(runId, "Application stopped or reloaded"));
        } catch (RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: cannot end every open run, stopping the run threads anyway");
        } finally {
            stopThreads();
        }
    }

    private void stopThreads() {
        executor.shutdownNow();
        dispatcher.shutdown();
        try {
            if (!dispatcher.awaitTermination(2, TimeUnit.SECONDS)) {
                dispatcher.shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatcher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** @return whether the manager was shut down: its listeners serve no run any more */
    public boolean isShutdown() {
        return shutdown;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
