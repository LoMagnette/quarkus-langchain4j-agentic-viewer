package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    /** The systems registered already, looked up without side effect (from the threads of the application). */
    private final Function<String, Optional<AgenticSystem>> knownSystems;
    private final RunInvoker invoker;
    private final ExecutorService executor;
    /** One thread delivers every event in order, so emitting never waits for the listeners. */
    private final ThreadPoolExecutor dispatcher;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    /** Run ids per history ({@link #historyKey}: Dev UI or application runs of a system), oldest first; guarded by itself. */
    private final Map<String, Deque<String>> history = new HashMap<>();
    private final int historySize;
    /** Runs the application starts itself: off unless enabled (the config, or a test); the Dev UI may pause them. */
    private volatile boolean applicationRunsEnabled;
    private volatile boolean recordingApplicationRuns = true;
    private volatile int applicationHistorySize = DEFAULT_HISTORY_SIZE;
    private volatile int maxOpenApplicationRuns = DEFAULT_MAX_CONCURRENT_RUNS;
    /** Application runs not ended yet, per system. */
    private final Map<String, AtomicInteger> openApplicationRuns = new ConcurrentHashMap<>();
    /** Application runs not recorded (too many open, or events piling up), per system. */
    private final Map<String, AtomicLong> skippedApplicationRuns = new ConcurrentHashMap<>();
    /** Series by id; their ids per system, oldest first (guarded by the map). */
    private final Map<String, Series> series = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> seriesHistory = new HashMap<>();
    /** One thread per series running: it starts the series' runs one after the other. */
    private final ExecutorService seriesExecutor;
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
    private static final String STOPPING = "The application is stopping or reloading: start the run again once it is back";
    private static final long EVENT_THREAD_IDLE_SECONDS = 5;
    /** Events waiting for the event thread beyond which a new application run is skipped: the viewer falls behind. */
    static final int MAX_QUEUED_EVENTS = 50_000;
    private static final String APPLICATION_HISTORY = "\u0000application";
    private static final String SERIES_HISTORY = "\u0000series:";
    /** Runs a series may ask for. */
    public static final int MAX_SERIES_RUNS = 50;
    /** Series kept per system: an older ended one leaves, with its runs. */
    static final int SERIES_KEPT = 5;

    @Inject
    public RunManager(AgenticSystemRegistry registry, BuilderSystems builderSystems, AgenticViewerRuntimeConfig config) {
        this(registry::find, registry::known, new CdiRunInvoker(builderSystems::root), config.maxPayloadSize(),
                config.historySize(), config.maxConcurrentRuns());
        if (config.applicationRuns()) {
            enableApplicationRuns(config.applicationHistorySize(), config.maxOpenApplicationRuns());
        }
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
        this(systems, systems, invoker, maxPayloadSize, historySize, maxConcurrentRuns);
    }

    private RunManager(Function<String, Optional<AgenticSystem>> systems,
            Function<String, Optional<AgenticSystem>> knownSystems, RunInvoker invoker, int maxPayloadSize,
            int historySize, int maxConcurrentRuns) {
        this.systems = systems;
        this.knownSystems = knownSystems;
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
        this.seriesExecutor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "agentic-viewer-series-" + THREADS.incrementAndGet());
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
     * @throws IllegalStateException when {@code max-concurrent-runs} runs are running, or the manager is shut down (no
     *         run is created)
     */
    public String startRun(String systemId, Map<String, Object> inputs) {
        return accept(systemId, inputs, null);
    }

    /**
     * @param of the series the run belongs to: it then waits for a free run thread (until the series is stopped)
     *        instead of being refused
     */
    private String accept(String systemId, Map<String, Object> inputs, Series of) {
        AgenticSystem system = systems.apply(systemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
        if (shutdown) {
            throw new IllegalStateException(STOPPING);
        }
        if (of != null) {
            awaitRunThread(of);
        } else if (!runThreads.tryAcquire()) {
            throw new IllegalStateException("Too many runs at once: " + maxConcurrentRuns + " are running (cancelled ones "
                    + "count until their thread returns); start it again once one ended, or raise "
                    + "quarkus.langchain4j.agentic.dev-ui.max-concurrent-runs");
        }
        Run accepted = null;
        try {
            // a copy: the run executes and its events are written later, the caller's map may change meanwhile (null
            // values stay: an optional input left empty)
            Map<String, Object> runInputs = inputs == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            Run run = new Run(UUID.randomUUID().toString(), systemId, RunOrigin.DEV_UI, of == null ? null : of.id(),
                    runInputs, System.currentTimeMillis());
            accepted = run;
            runs.put(run.id(), run);
            remember(run);
            emit(run, new RunStarted(run.id(), run.startedAt(), systemId, runInputs));
            // known to the run before it starts: a cancel at any time finds it
            FutureTask<Void> task = new FutureTask<>(() -> execute(run, system), null);
            run.task(task);
            // from here the permit is given back when the thread is done with the run, also after a cancel (even before
            // it ran)
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    runThreads.release();
                }
            });
            return run.id();
        } catch (RuntimeException e) {
            // not accepted (e.g. a shutdown meanwhile rejects its event or its thread): nothing is left behind
            runThreads.release();
            if (accepted != null) {
                forget(accepted);
            }
            throw shutdown ? new IllegalStateException(STOPPING, e) : e;
        }
    }

    /** Takes a run thread permit for the next run of a series, waiting while the others use them. */
    private void awaitRunThread(Series of) {
        try {
            while (!runThreads.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                if (of.cancelled() || shutdown) {
                    throw new CancellationException("Series stopped");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Series stopped");
        }
    }

    /**
     * Starts a series: the system runs {@code count} times with the same inputs, one run after the other, on a thread of
     * its own; returns at once. Its runs are Dev UI runs (their human steps wait for the Dev UI) kept with the series,
     * out of the system's history; {@value #SERIES_KEPT} series are kept per system.
     *
     * @return the series id
     * @throws IllegalArgumentException for an unknown system or a count out of 1..{@value #MAX_SERIES_RUNS}
     * @throws IllegalStateException when the manager is shut down
     */
    public String startSeries(String systemId, Map<String, Object> inputs, int count) {
        if (count < 1 || count > MAX_SERIES_RUNS) {
            throw new IllegalArgumentException("A series runs a system 1 to " + MAX_SERIES_RUNS + " times, not " + count);
        }
        systems.apply(systemId).orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
        if (shutdown) {
            throw new IllegalStateException(STOPPING);
        }
        Map<String, Object> seriesInputs = inputs == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        Series started = new Series(UUID.randomUUID().toString(), systemId, seriesInputs, count, System.currentTimeMillis());
        series.put(started.id(), started);
        synchronized (seriesHistory) {
            Deque<String> ids = seriesHistory.computeIfAbsent(systemId, id -> new ArrayDeque<>());
            ids.addLast(started.id());
            pruneSeries(ids);
        }
        try {
            seriesExecutor.execute(() -> execute(started));
        } catch (RejectedExecutionException e) {
            started.end(RunStatus.CANCELLED, STOPPING);
            throw new IllegalStateException(STOPPING, e);
        }
        return started.id();
    }

    /** Keeps the newest {@value #SERIES_KEPT} series of a system, and every one still running; a dropped one takes its runs. */
    private void pruneSeries(Deque<String> ids) {
        long kept = ids.size();
        for (var it = ids.iterator(); kept > SERIES_KEPT && it.hasNext();) {
            Series old = series.get(it.next());
            if (old == null || old.status() != RunStatus.RUNNING) {
                it.remove();
                kept--;
                if (old != null) {
                    series.remove(old.id());
                    old.runIds().forEach(runs::remove);
                    synchronized (history) {
                        history.remove(SERIES_HISTORY + old.id());
                    }
                }
            }
        }
    }

    private void execute(Series of) {
        try {
            for (int i = 0; i < of.count() && !of.cancelled() && !shutdown; i++) {
                String runId = accept(of.systemId(), of.inputs(), of);
                of.started(runId);
                if (of.cancelled()) {
                    cancel(runId); // stopped while it started
                }
                Run run = runs.get(runId);
                if (run != null) {
                    run.completion().get();
                }
            }
            of.end(of.cancelled() || shutdown ? RunStatus.CANCELLED : RunStatus.SUCCEEDED, null);
        } catch (CancellationException e) {
            of.end(RunStatus.CANCELLED, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            of.end(RunStatus.CANCELLED, null);
        } catch (ExecutionException | RuntimeException e) {
            // e.g. the system is gone (a hot reload)
            of.end(RunStatus.FAILED, e.getMessage());
        }
    }

    /**
     * Stops a series: no further run starts, the one running is cancelled.
     *
     * @return whether it was running
     */
    public boolean cancelSeries(String seriesId) {
        Series of = seriesId == null ? null : series.get(seriesId);
        if (of == null || of.status() != RunStatus.RUNNING) {
            return false;
        }
        of.cancel();
        String current = of.currentRunId();
        if (current != null) {
            cancel(current);
        }
        return true;
    }

    /**
     * @return the series with the results of its runs so far, empty for an unknown (or dropped) one
     */
    public Optional<SeriesTrace> series(String seriesId) {
        Series of = seriesId == null ? null : series.get(seriesId);
        if (of == null) {
            return Optional.empty();
        }
        List<SeriesRun> results = new ArrayList<>();
        List<String> ids = of.runIds();
        for (int i = 0; i < ids.size(); i++) {
            Run run = runs.get(ids.get(i));
            if (run != null) {
                results.add(result(i + 1, run));
            }
        }
        return Optional.of(new SeriesTrace(of.id(), of.systemId(), of.inputs(), of.count(), of.status(), of.startedAt(),
                of.durationMillis(), of.error(), results));
    }

    /** A run as the results of its series show it: from its recorded (bounded) events. */
    private static SeriesRun result(int index, Run run) {
        long tokens = 0;
        Object output = null;
        ErrorInfo error = null;
        List<String> path = new ArrayList<>();
        for (ExecutionEvent event : run.events()) {
            switch (event) {
                case AgentStarted started -> path.add(started.nodeId());
                case AgentFinished finished when finished.tokens() != null && finished.tokens().totalTokens() != null ->
                    tokens += finished.tokens().totalTokens();
                case RunFinished finished -> output = finished.output();
                case RunFailed failed -> error = failed.error();
                default -> {
                }
            }
        }
        return new SeriesRun(index, run.id(), run.status(), run.durationMillis(), tokens, output, error, path);
    }

    /**
     * @return the series of a system kept, newest first
     */
    public List<SeriesSummary> listSeries(String systemId) {
        List<String> ids = new ArrayList<>();
        synchronized (seriesHistory) {
            seriesHistory.getOrDefault(systemId, new ArrayDeque<>()).descendingIterator().forEachRemaining(ids::add);
        }
        return ids.stream().map(series::get).filter(Objects::nonNull).map(SeriesSummary::of).toList();
    }

    /** A run that was never accepted (its id was not given out): ended, and out of the runs and the history. */
    private void forget(Run run) {
        run.claimEnd(0);
        run.end(RunStatus.CANCELLED, null); // its events, if still queued, are not delivered any more
        runs.remove(run.id());
        synchronized (history) {
            Deque<String> ids = history.get(historyKey(run));
            if (ids != null) {
                ids.remove(run.id());
            }
        }
    }

    /**
     * The history a run is kept in: the Dev UI runs and the application runs of a system have one each, so application
     * runs never push the Dev UI ones out.
     */
    private static String historyKey(Run run) {
        if (run.seriesId() != null) {
            return SERIES_HISTORY + run.seriesId(); // kept with its series, out of the system's history
        }
        return run.origin() == RunOrigin.APPLICATION ? run.systemId() + APPLICATION_HISTORY : run.systemId();
    }

    /** Adds the run to its system's history. */
    private void remember(Run run) {
        synchronized (history) {
            history.computeIfAbsent(historyKey(run), id -> new ArrayDeque<>()).addLast(run.id());
            prune(run, null);
        }
    }

    /**
     * Keeps the newest ended runs of the history of a run ({@code historySize}, {@code application-history-size} for
     * application runs) and every run still running (they are only dropped once ended, oldest first): when a run
     * starts and when one ends.
     *
     * @param ending a run ending now, counted as ended
     */
    private void prune(Run run, Run ending) {
        int size = run.seriesId() != null ? MAX_SERIES_RUNS
                : run.origin() == RunOrigin.APPLICATION ? applicationHistorySize : historySize;
        synchronized (history) {
            Deque<String> ids = history.get(historyKey(run));
            if (ids == null) {
                return;
            }
            long ended = ids.stream().map(runs::get).filter(r -> r == null || r == ending || r.ended()).count();
            for (var it = ids.iterator(); ended > size && it.hasNext();) {
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
     * @return the runs of a system kept in the history, Dev UI and application ones, newest first
     */
    public List<RunSummary> listRuns(String systemId) {
        List<String> ids = new ArrayList<>();
        synchronized (history) {
            // each history newest first, then merged by start time (a stable sort: runs started within the same
            // millisecond keep that order)
            history.getOrDefault(systemId, new ArrayDeque<>()).descendingIterator().forEachRemaining(ids::add);
            history.getOrDefault(systemId + APPLICATION_HISTORY, new ArrayDeque<>()).descendingIterator()
                    .forEachRemaining(ids::add);
        }
        return ids.stream().map(runs::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(Run::startedAt).reversed()).map(RunSummary::of).toList();
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
     * @return whether the run was running and is now cancelled; false for an application run (observed only)
     */
    public boolean cancel(String runId) {
        Run run = run(runId).orElse(null);
        if (run == null || run.origin() == RunOrigin.APPLICATION || !run.claimEnd(elapsedSince(run))) {
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
     * Records the runs the application starts itself ({@link #startApplicationRun}): on with the
     * {@code application-runs} config, off for a manager built in code unless enabled.
     *
     * @param historySize ended application runs kept per system
     * @param maxOpenRuns application runs of one system recorded at the same time
     * @return this manager
     */
    public RunManager enableApplicationRuns(int historySize, int maxOpenRuns) {
        applicationHistorySize = Math.max(1, historySize);
        maxOpenApplicationRuns = Math.max(1, maxOpenRuns);
        applicationRunsEnabled = true;
        return this;
    }

    /** Pauses or resumes the recording of application runs (the switch of the Dev UI); nothing when they are off. */
    public void recordApplicationRuns(boolean record) {
        recordingApplicationRuns = record;
    }

    /**
     * @return whether application runs are recorded, and how many of a system were not
     */
    public ApplicationRuns applicationRuns(String systemId) {
        AtomicLong skipped = systemId == null ? null : skippedApplicationRuns.get(systemId);
        return new ApplicationRuns(applicationRunsEnabled, recordingApplicationRuns, skipped == null ? 0 : skipped.get());
    }

    /**
     * Records a run the application started (a root agent called outside a Dev UI run, on the application's thread,
     * which executes it); {@link #endApplicationRun} ends it. Never blocks nor throws.
     *
     * @return the run; empty when application runs are off or paused, the manager is shut down, the system is not
     *         registered, or (counted as skipped) the system has {@code max-open-application-runs} open ones or the
     *         event thread falls behind
     */
    Optional<Run> startApplicationRun(String systemId, Map<String, Object> inputs) {
        if (!applicationRunsEnabled || !recordingApplicationRuns || shutdown || systemId == null
                || system(systemId).isEmpty()) {
            return Optional.empty();
        }
        AtomicInteger open = openApplicationRuns.computeIfAbsent(systemId, id -> new AtomicInteger());
        if (open.incrementAndGet() > maxOpenApplicationRuns || dispatcher.getQueue().size() > MAX_QUEUED_EVENTS) {
            open.decrementAndGet();
            skippedApplicationRuns.computeIfAbsent(systemId, id -> new AtomicLong()).incrementAndGet();
            return Optional.empty();
        }
        // copied on this thread: the application may change its map once the call returns
        Map<String, Object> runInputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        Run run = new Run(UUID.randomUUID().toString(), systemId, RunOrigin.APPLICATION, runInputs,
                System.currentTimeMillis());
        try {
            runs.put(run.id(), run);
            remember(run);
            emit(run, new RunStarted(run.id(), run.startedAt(), systemId, runInputs));
            return Optional.of(run);
        } catch (RuntimeException e) {
            // e.g. a shutdown meanwhile rejects its event: the application's call goes on unrecorded
            open.decrementAndGet();
            forget(run);
            return Optional.empty();
        }
    }

    /**
     * Ends an application run when its root call returns or fails (or a shutdown ended it before): never throws.
     *
     * @param error the failure of the root call, null when it returned
     */
    void endApplicationRun(Run run, Object output, Throwable error) {
        if (!run.claimEnd(elapsedSince(run))) {
            return;
        }
        openApplicationRuns.computeIfPresent(run.systemId(), (id, open) -> {
            open.decrementAndGet();
            return open;
        });
        try {
            if (error == null) {
                emit(run, new RunFinished(run.id(), System.currentTimeMillis(), output, run.durationMillis()));
                end(run, RunStatus.SUCCEEDED, output);
            } else {
                emit(run, new RunFailed(run.id(), System.currentTimeMillis(), ErrorInfo.of(error), run.durationMillis()));
                end(run, RunStatus.FAILED, null);
            }
        } catch (RejectedExecutionException e) {
            // shut down meanwhile: the shutdown ended the runs it saw open
        }
    }

    /**
     * The application runs of a system as they start, until the subscriber cancels (the Dev UI follows them); each
     * subscriber receives on a worker thread.
     */
    public Multi<RunSummary> streamApplicationRuns(String systemId) {
        return Multi.createFrom().<RunSummary> emitter(emitter -> {
            Consumer<ExecutionEvent> listener = event -> {
                if (event instanceof RunStarted started && Objects.equals(started.systemId(), systemId)) {
                    run(started.runId()).filter(run -> run.origin() == RunOrigin.APPLICATION)
                            .ifPresent(run -> emitter.emit(RunSummary.of(run)));
                }
            };
            emitter.onTermination(() -> listeners.remove(listener));
            listeners.add(listener);
        }).emitOn(Infrastructure.getDefaultWorkerPool());
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
            prune(run, run); // before the run completes: whoever waits on it sees the bounded history
            run.end(status, output);
        });
    }

    /**
     * @return the run executed by the current thread, if it is a run thread
     */
    static Optional<Run> currentRun() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** @return a system registered already (looked up from agent threads: no builder system is discovered) */
    Optional<AgenticSystem> system(String systemId) {
        return knownSystems.apply(systemId);
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
            emitter.onTermination(() -> {
                try {
                    dispatcher.execute(() -> run.unsubscribe(emitter));
                } catch (RejectedExecutionException e) {
                    // shut down (e.g. the run ended by the shutdown completes this stream on the stopping event
                    // thread): nothing is delivered any more, the subscriber is left on the run
                }
            });
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
        series.values().forEach(Series::cancel);
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
        seriesExecutor.shutdownNow();
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
