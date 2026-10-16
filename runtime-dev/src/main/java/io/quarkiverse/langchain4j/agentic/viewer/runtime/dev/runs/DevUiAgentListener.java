package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeDestroyed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.TokenUsage;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;

/**
 * Turns the agentic callbacks of Dev UI runs into execution events. Attached to every root agent and inherited by its
 * sub-agents ({@link RootListeners}). Callbacks run on the agent threads: they only build the event and queue it
 * ({@link RunManager#emit}), never block, and never stringify outputs (a streaming response would block).
 * <p>
 * A run is found through its AgenticScope: the scope is created on the run thread, which knows its run; later
 * callbacks, on any thread, carry the scope. Callbacks of invocations not started by the Dev UI are ignored.
 */
public class DevUiAgentListener implements AgentListener {

    private static final AtomicLong INVOCATIONS = new AtomicLong();
    private static final List<String> STREAM_TYPES = List.of("dev.langchain4j.service.TokenStream",
            "io.smallrye.mutiny.Multi", "java.util.concurrent.Flow$Publisher");

    private final RunManager runManager;
    private final Map<AgenticScope, Run> runsByScope = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Invocations in progress on each thread, innermost last. */
    private final ThreadLocal<Deque<Invocation>> threadInvocations = ThreadLocal.withInitial(ArrayDeque::new);
    /**
     * Invocations in progress per agent id, for the parent of a branch running on another thread. Agents are matched by
     * {@link AgentInstance#agentId()} (as AgentMonitor does): callbacks may hand different objects for the same agent.
     */
    private final Map<String, Deque<Invocation>> openInvocations = new HashMap<>();
    private final Map<String, Long> toolStarts = new ConcurrentHashMap<>();

    private record Invocation(String agentId, String id, long startNanos) {
    }

    public DevUiAgentListener(RunManager runManager) {
        this.runManager = runManager;
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        // only runs of this listener's manager: listeners stay attached to the roots for the life of the application
        RunManager.currentRun().filter(run -> runManager.run(run.id()).orElse(null) == run).ifPresent(run -> {
            runsByScope.put(scope, run);
            runManager.emit(run, new ScopeCreated(run.id(), now(), String.valueOf(scope.memoryId())));
        });
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        Run run = runsByScope.remove(scope);
        if (run != null) {
            runManager.emit(run, new ScopeDestroyed(run.id(), now(), String.valueOf(scope.memoryId())));
        }
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        Run run = runOf(request.agenticScope());
        if (run == null) {
            return;
        }
        AgentInstance agent = request.agent();
        String parentInvocation = openInvocation(agent.parent()).map(Invocation::id).orElse(null);
        Invocation invocation = new Invocation(agent.agentId(), "inv-" + INVOCATIONS.incrementAndGet(), System.nanoTime());
        threadInvocations.get().addLast(invocation);
        synchronized (openInvocations) {
            openInvocations.computeIfAbsent(agent.agentId(), a -> new ArrayDeque<>()).addLast(invocation);
        }
        runManager.emit(run, new AgentStarted(run.id(), now(), nodeId(run, agent), invocation.id(), parentInvocation,
                request.agentName(), null, copy(request.inputs()), isStreaming(agent)));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Run run = runOf(response.agenticScope());
        Invocation invocation = close(response.agent());
        if (run != null && invocation != null) {
            runManager.emit(run, new AgentFinished(run.id(), now(), nodeId(run, response.agent()), invocation.id(),
                    response.output(), tokens(response.chatResponse()), elapsedMillis(invocation)));
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        Run run = runOf(error.agenticScope());
        Invocation invocation = close(error.agent());
        if (run != null && invocation != null) {
            runManager.emit(run, new AgentFailed(run.id(), now(), nodeId(run, error.agent()), invocation.id(),
                    ErrorInfo.of(error.error()), elapsedMillis(invocation)));
        }
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
        Run run = runOf(execution.agenticScope());
        if (run == null) {
            return;
        }
        ToolExecutionRequest request = execution.toolExecution().request();
        toolStarts.put(toolKey(run, request), System.nanoTime());
        runManager.emit(run, new ToolStarted(run.id(), now(), callingInvocation(execution.agentInstance()), request.id(),
                request.name(), request.arguments()));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
        Run run = runOf(execution.agenticScope());
        if (run == null) {
            return;
        }
        ToolExecutionRequest request = execution.toolExecution().request();
        Long start = toolStarts.remove(toolKey(run, request));
        runManager.emit(run, new ToolFinished(run.id(), now(), callingInvocation(execution.agentInstance()), request.id(),
                request.name(), execution.toolExecution().result(), start == null ? 0 : (System.nanoTime() - start) / 1_000_000));
    }

    private Run runOf(AgenticScope scope) {
        return scope == null ? null : runsByScope.get(scope);
    }

    /** The innermost invocation of this agent in progress: on this thread first, else on any thread. */
    private Optional<Invocation> openInvocation(AgentInstance agent) {
        if (agent == null) {
            return Optional.empty();
        }
        Deque<Invocation> local = threadInvocations.get();
        for (var it = local.descendingIterator(); it.hasNext();) {
            Invocation invocation = it.next();
            if (invocation.agentId().equals(agent.agentId())) {
                return Optional.of(invocation);
            }
        }
        synchronized (openInvocations) {
            Deque<Invocation> open = openInvocations.get(agent.agentId());
            return open == null ? Optional.empty() : Optional.ofNullable(open.peekLast());
        }
    }

    private Invocation close(AgentInstance agent) {
        Deque<Invocation> local = threadInvocations.get();
        Invocation closed = null;
        for (var it = local.descendingIterator(); it.hasNext();) {
            Invocation invocation = it.next();
            if (invocation.agentId().equals(agent.agentId())) {
                it.remove();
                closed = invocation;
                break;
            }
        }
        if (closed != null) {
            synchronized (openInvocations) {
                Deque<Invocation> open = openInvocations.get(agent.agentId());
                if (open != null) {
                    open.remove(closed);
                    if (open.isEmpty()) {
                        openInvocations.remove(agent.agentId());
                    }
                }
            }
        }
        return closed;
    }

    private String callingInvocation(AgentInstance agent) {
        return openInvocation(agent).map(Invocation::id).orElse(null);
    }

    /**
     * The topology node of an agent instance, following the TopologyBuilder rules: the root is the system id, each
     * level adds the simple class name of the sub-agent, with ~n when its parent has several sub-agents of that class.
     */
    String nodeId(Run run, AgentInstance agent) {
        AgentInstance parent = agent.parent();
        if (parent == null) {
            return run.systemId();
        }
        String segment = simpleName(agent.type());
        int occurrence = 0;
        for (AgentInstance sibling : parent.subagents()) {
            if (simpleName(sibling.type()).equals(segment)) {
                occurrence++;
            }
            if (sibling.agentId().equals(agent.agentId())) {
                break;
            }
        }
        return nodeId(run, parent) + "/" + segment + (occurrence > 1 ? "~" + occurrence : "");
    }

    private static String simpleName(Class<?> type) {
        String name = type == null ? "?" : type.getName();
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
    }

    private static boolean isStreaming(AgentInstance agent) {
        Type type = agent.outputType();
        String name = type == null ? "" : type.getTypeName();
        return STREAM_TYPES.stream().anyMatch(name::startsWith);
    }

    private static TokenUsage tokens(ChatResponse response) {
        if (response == null || response.metadata() == null || response.metadata().tokenUsage() == null) {
            return null;
        }
        var usage = response.metadata().tokenUsage();
        return new TokenUsage(usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }

    private static Map<String, Object> copy(Map<String, Object> inputs) {
        return inputs == null ? Map.of() : new HashMap<>(inputs);
    }

    private static String toolKey(Run run, ToolExecutionRequest request) {
        return run.id() + "/" + (request.id() != null ? request.id() : request.name() + "@" + Thread.currentThread().getId());
    }

    private static long elapsedMillis(Invocation invocation) {
        return (System.nanoTime() - invocation.startNanos()) / 1_000_000;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
