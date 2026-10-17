package io.quarkiverse.langchain4j.agentic.viewer.it.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T6.3 (TASK-61): what the Dev UI listener costs per agent invocation, on a system of 50 instant non-AI agents (so the
 * overhead is not hidden by model calls). Not part of the build (the name does not end with Test): run it with
 * {@code mvn test -pl integration-tests -Dtest=ListenerOverheadBenchmark}.
 */
@QuarkusTest
class ListenerOverheadBenchmark {

    private static final int AGENTS = 50;
    private static final int WARMUP = 300;
    private static final int MEASURED = 1000;

    /** A sequence of 50 agents, each writing one scope key. */
    private static UntypedAgent system() {
        List<Object> agents = new ArrayList<>();
        for (int i = 0; i < AGENTS; i++) {
            String key = "key" + i;
            agents.add(AgenticServices.agentAction(scope -> scope.writeState(key, key)));
        }
        return AgenticServices.sequenceBuilder().name("fifty").subAgents(agents).build();
    }

    private static final int ROUNDS = 5;

    /** Median duration of one call, in microseconds. */
    private static double median(Runnable call) {
        long[] durations = new long[MEASURED];
        for (int i = 0; i < MEASURED; i++) {
            long start = System.nanoTime();
            call.run();
            durations[i] = System.nanoTime() - start;
        }
        Arrays.sort(durations);
        return durations[MEASURED / 2] / 1000.0;
    }

    /**
     * The best median of each call over several rounds, the calls measured in turn (so JIT warm-up and machine noise
     * do not favour the one measured last), after a warm-up of all.
     */
    private static double[] best(Runnable... calls) {
        for (Runnable call : calls) {
            for (int i = 0; i < WARMUP; i++) {
                call.run();
            }
        }
        double[] best = new double[calls.length];
        Arrays.fill(best, Double.MAX_VALUE);
        for (int round = 0; round < ROUNDS; round++) {
            for (int c = 0; c < calls.length; c++) {
                best[c] = Math.min(best[c], median(calls[c]));
            }
        }
        return best;
    }

    @Test
    void listenerOverheadPerInvocation() {
        UntypedAgent plain = system();

        UntypedAgent observed = system();
        AgenticSystem topology = BuilderTopology.system("fifty", "fifty", null, (AgentInstance) observed);
        RunManager runManager = new RunManager(id -> Optional.of(topology), new CdiRunInvoker(id -> Optional.of(observed)));
        RootListeners.attach("fifty", observed, new DevUiAgentListener(runManager));
        Supplier<Run> devUiRun = () -> runManager.run(runManager.startRun("fifty", Map.of())).orElseThrow();
        double[] best = best(
                () -> plain.invoke(Map.of()),
                // the application's own calls: the listener is there, but they are no Dev UI run
                () -> observed.invoke(Map.of()),
                // Dev UI runs: every invocation becomes events (the run thread hop included)
                () -> {
                    try {
                        devUiRun.get().completion().get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        double withoutListener = best[0];
        double appCalls = best[1];
        double devUiRuns = best[2];
        int invocations = AGENTS + 1;
        System.out.printf("%nLISTENER OVERHEAD (%d agent invocations per call, best median of %d rounds of %d calls)%n", invocations, ROUNDS, MEASURED);
        System.out.printf("  no listener:                 %8.1f us per call, %6.2f us per invocation%n", withoutListener,
                withoutListener / invocations);
        System.out.printf("  listener, application call:  %8.1f us per call, %6.2f us per invocation (+%.2f)%n", appCalls,
                appCalls / invocations, (appCalls - withoutListener) / invocations);
        System.out.printf("  listener, Dev UI run:        %8.1f us per call, %6.2f us per invocation (+%.2f)%n", devUiRuns,
                devUiRuns / invocations, (devUiRuns - withoutListener) / invocations);
        runManager.shutdown();
    }
}
