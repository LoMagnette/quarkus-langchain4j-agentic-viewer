package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * Starts runs of agentic systems on its own worker threads and reports their lifecycle as events.
 * Constructible without CDI from a system lookup and an invoker, so tests can drive real or fake systems.
 */
@ApplicationScoped
public class RunManager {

    private static final Logger LOG = Logger.getLogger(RunManager.class);
    private static final AtomicInteger THREADS = new AtomicInteger();

    private final Function<String, Optional<AgenticSystem>> systems;
    private final RunInvoker invoker;
    private final ExecutorService executor;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final List<Consumer<ExecutionEvent>> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public RunManager(AgenticSystemRegistry registry) {
        this(registry::find, new CdiRunInvoker());
    }

    public RunManager(Function<String, Optional<AgenticSystem>> systems, RunInvoker invoker) {
        this.systems = systems;
        this.invoker = invoker;
        // run threads load the application classes: they keep the class loader of whoever created the manager
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        this.executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "agentic-viewer-run-" + THREADS.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(classLoader);
            return thread;
        });
    }

    /**
     * Starts a run and returns at once; the run executes on a worker thread.
     *
     * @param inputs input values by name
     * @return the run id
     * @throws IllegalArgumentException for an unknown system
     */
    public String startRun(String systemId, Map<String, Object> inputs) {
        AgenticSystem system = systems.apply(systemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
        Map<String, Object> runInputs = inputs == null ? Map.of() : inputs;
        Run run = new Run(UUID.randomUUID().toString(), systemId, runInputs, System.currentTimeMillis());
        runs.put(run.id(), run);
        emit(run, new RunStarted(run.id(), run.startedAt(), systemId, runInputs));
        executor.execute(() -> execute(run, system));
        return run.id();
    }

    private void execute(Run run, AgenticSystem system) {
        long start = System.nanoTime();
        try {
            Object output = invoker.invoke(system, run.inputs());
            emit(run, new RunFinished(run.id(), System.currentTimeMillis(), output, elapsedMillis(start)));
            run.end(RunStatus.SUCCEEDED, output);
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException e && e.getCause() != null ? e.getCause() : error;
            emit(run, new RunFailed(run.id(), System.currentTimeMillis(), ErrorInfo.of(cause), elapsedMillis(start)));
            run.end(RunStatus.FAILED, null);
        }
    }

    public Optional<Run> run(String runId) {
        return Optional.ofNullable(runId).map(runs::get);
    }

    /**
     * Adds a listener receiving every event of every run, on the thread that emits it: it must not block.
     */
    public void addListener(Consumer<ExecutionEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Records an event of a run and passes it to the listeners.
     */
    public void emit(Run run, ExecutionEvent event) {
        run.add(event);
        for (Consumer<ExecutionEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOG.warnf(e, "Agentic viewer event listener failed on %s", event.getType());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
