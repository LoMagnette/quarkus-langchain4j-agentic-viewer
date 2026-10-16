package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorHandled;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeDestroyed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.TokenUsage;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

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
    private static final Pattern ITEM_SUFFIX = Pattern.compile(".*_(\\d+)");
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

    /** One error observer per run, so the scope's handler is wrapped once. */
    private final Map<String, ErrorHandlers.Observer> errorObservers = new ConcurrentHashMap<>();

    /** Node kinds of each system's topology, by node id. */
    private final Map<String, Map<String, AgentNode>> nodesBySystem = new ConcurrentHashMap<>();

    /**
     * An invocation in progress, with what its children need: the loop counter of a loop, the items of a parallel
     * mapper and the indexes already given to its children.
     */
    private static final class Invocation {
        final String agentId;
        final String id;
        final long startNanos;
        final String nodeId;
        final Integer iteration;
        final Integer itemIndex;
        int loopCounter = -1;
        List<Object> items;
        final Set<Integer> takenItems = new HashSet<>();
        /** Starts of children per class name, to tell apart a sub-agent listed several times. */
        final Map<String, Integer> childStarts = new HashMap<>();

        Invocation(String agentId, String id, String nodeId, Integer iteration, Integer itemIndex) {
            this.agentId = agentId;
            this.id = id;
            this.startNanos = System.nanoTime();
            this.nodeId = nodeId;
            this.iteration = iteration;
            this.itemIndex = itemIndex;
        }

        String id() {
            return id;
        }

        String agentId() {
            return agentId;
        }

        long startNanos() {
            return startNanos;
        }
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

    /**
     * Reports what the error handler decided for a failing agent: called on the thread of the workflow running it.
     */
    private ErrorHandlers.Observer errorObserver(Run run) {
        return (context, result, handlerFailure) -> {
            Invocation enclosing = threadInvocations.get().peekLast();
            String nodeId = failingNode(run, enclosing, context.agentName());
            Throwable error = context.exception() != null ? context.exception() : handlerFailure;
            runManager.emit(run, new ErrorHandled(run.id(), now(), nodeId, enclosing == null ? null : enclosing.id(),
                    context.agentName(), error == null ? null : ErrorInfo.of(error),
                    result == null ? null : result.type().name(), result == null ? null : result.result()));
        };
    }

    /** The node of the failing agent: a child of the enclosing invocation's node with that name, else any such node. */
    private String failingNode(Run run, Invocation enclosing, String agentName) {
        AgentNode node = enclosing == null ? null : node(run, enclosing.nodeId);
        if (node != null) {
            if (agentName != null && agentName.equals(node.name())) {
                return node.id();
            }
            for (AgentNode child : node.children()) {
                if (child.name().equals(agentName)) {
                    return child.id();
                }
            }
        }
        node(run, run.systemId()); // makes sure the topology is indexed
        return nodesBySystem.getOrDefault(run.systemId(), Map.of()).values().stream()
                .filter(n -> n.name().equals(agentName)).map(AgentNode::id).sorted().findFirst().orElse(null);
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        Run run = runsByScope.remove(scope);
        if (run != null) {
            errorObservers.remove(run.id());
        }
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
        // every workflow sets its error handler on the scope when it starts: keep it observed
        ErrorHandlers.observe(request.agenticScope(), errorObservers.computeIfAbsent(run.id(), id -> errorObserver(run)));
        AgentInstance agent = request.agent();
        Invocation parent = openInvocation(agent.parent()).orElse(null);
        String nodeId = parent == null ? nodeId(run, agent) : childNodeId(run, parent, agent);
        Map<String, Object> inputs = copy(request.inputs());
        Integer iteration = parent == null ? null : parent.iteration;
        Integer itemIndex = parent == null ? null : parent.itemIndex;
        AgentNode parentNode = parent == null ? null : node(run, parent.nodeId);
        if (parentNode != null && parentNode.kind() == NodeKind.LOOP) {
            synchronized (parent) {
                // an iteration starts with the first agent of the loop body
                if (parentNode.children().isEmpty() || parentNode.children().get(0).id().equals(nodeId)
                        || parent.loopCounter < 0) {
                    parent.loopCounter++;
                }
                iteration = parent.loopCounter;
            }
        } else if (parentNode != null && parentNode.kind() == NodeKind.PARALLEL_MAPPER) {
            itemIndex = itemIndex(parent, request.agentName(), inputs);
        }
        Invocation invocation = new Invocation(agent.agentId(), "inv-" + INVOCATIONS.incrementAndGet(), nodeId, iteration,
                itemIndex);
        AgentNode node = node(run, nodeId);
        if (node != null && node.kind() == NodeKind.PARALLEL_MAPPER) {
            invocation.items = items(inputs);
        }
        threadInvocations.get().addLast(invocation);
        synchronized (openInvocations) {
            openInvocations.computeIfAbsent(agent.agentId(), a -> new ArrayDeque<>()).addLast(invocation);
        }
        runManager.emit(run, new AgentStarted(run.id(), now(), nodeId, invocation.id(), parent == null ? null : parent.id,
                request.agentName(), iteration, itemIndex, inputs, isStreaming(agent)));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Run run = runOf(response.agenticScope());
        Invocation invocation = close(response.agent());
        if (run != null && invocation != null) {
            long now = now();
            runManager.emit(run, new AgentFinished(run.id(), now, invocation.nodeId, invocation.id(),
                    response.output(), tokens(response.chatResponse()), elapsedMillis(invocation)));
            String outputKey = response.agent().outputKey();
            if (outputKey != null && !outputKey.isBlank()) {
                // the output is what the agent writes under its key (bounded later, on the event thread)
                runManager.emit(run, new ScopeStateChanged(run.id(), now, outputKey, response.output(), false,
                        invocation.nodeId));
            }
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        Run run = runOf(error.agenticScope());
        Invocation invocation = close(error.agent());
        if (run != null && invocation != null) {
            runManager.emit(run, new AgentFailed(run.id(), now(), invocation.nodeId, invocation.id(),
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

    /**
     * The item of a parallel mapper an invocation of its sub-agent runs for: the index, in the mapper's items, of the
     * first input value found there and not given yet (items may repeat); the next free index otherwise. Items run
     * concurrently, so their start order says nothing.
     */
    private static Integer itemIndex(Invocation mapper, String agentName, Map<String, Object> inputs) {
        synchronized (mapper) {
            // langchain4j names the invocation of each item <agent name>_<item index>
            Matcher suffix = ITEM_SUFFIX.matcher(agentName == null ? "" : agentName);
            if (suffix.matches()) {
                int index = Integer.parseInt(suffix.group(1));
                mapper.takenItems.add(index);
                return index;
            }
            List<Object> items = mapper.items == null ? List.of() : mapper.items;
            for (Object value : inputs.values()) {
                for (int i = 0; i < items.size(); i++) {
                    if (!mapper.takenItems.contains(i) && Objects.equals(items.get(i), value)) {
                        mapper.takenItems.add(i);
                        return i;
                    }
                }
            }
            int next = 0;
            while (mapper.takenItems.contains(next)) {
                next++;
            }
            mapper.takenItems.add(next);
            return next;
        }
    }

    /** The items a parallel mapper iterates on: its collection input. */
    private static List<Object> items(Map<String, Object> inputs) {
        for (Object value : inputs.values()) {
            if (value instanceof Collection<?> collection) {
                return new ArrayList<>(collection);
            }
            if (value != null && value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
                return new ArrayList<>(Arrays.asList((Object[]) value));
            }
        }
        return null;
    }

    private AgentNode node(Run run, String nodeId) {
        Map<String, AgentNode> nodes = nodesBySystem.computeIfAbsent(run.systemId(), systemId -> {
            Map<String, AgentNode> index = new HashMap<>();
            runManager.system(systemId).ifPresent(system -> indexNodes(system.root(), index));
            return index;
        });
        return nodes.get(nodeId);
    }

    private static void indexNodes(AgentNode node, Map<String, AgentNode> index) {
        index.put(node.id(), node);
        node.children().forEach(child -> indexNodes(child, index));
    }

    private Run runOf(AgenticScope scope) {
        return scope == null ? null : runsByScope.get(scope);
    }

    /**
     * @return the Dev UI run of this listener's manager that a scope belongs to, null for any other invocation
     */
    Run run(AgenticScope scope) {
        return runOf(scope);
    }

    /**
     * Called by an intercepted human-in-the-loop step of a run ({@link HumanInTheLoopSteps}), on its thread, once its
     * start is notified: reports that the step waits and blocks until the Dev UI answers or the run ends.
     *
     * @param valueType type the answer is converted to
     * @return the answer
     */
    Object awaitHumanInput(Run run, AgentInstance step, String prompt, String expectedKey, Type valueType,
            Map<String, Object> inputs) {
        Invocation invocation = openInvocation(step).orElse(null);
        AwaitingHumanInput waiting = new AwaitingHumanInput(run.id(), now(),
                invocation == null ? nodeId(run, step) : invocation.nodeId, invocation == null ? null : invocation.id(),
                prompt == null || prompt.isBlank() ? null : prompt, expectedKey,
                valueType == null ? null : valueType.getTypeName(), copy(inputs));
        return runManager.awaitHumanInput(run, waiting, valueType);
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
     * The topology node of a sub-agent invocation: a child of the parent invocation's node whose id ends with the
     * sub-agent's class name (TopologyBuilder rules). When the parent lists that class several times (/X, /X~2), the
     * k-th start under the parent invocation takes the k-th one, cycling for loops.
     */
    private String childNodeId(Run run, Invocation parent, AgentInstance agent) {
        String base = parent.nodeId + "/" + simpleName(agent.type());
        AgentNode parentNode = node(run, parent.nodeId);
        List<String> candidates = parentNode == null ? List.of()
                : parentNode.children().stream().map(AgentNode::id)
                        .filter(id -> id.equals(base) || id.startsWith(base + "~")).toList();
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? base : candidates.get(0);
        }
        synchronized (parent) {
            int start = parent.childStarts.merge(base, 1, Integer::sum) - 1;
            return candidates.get(start % candidates.size());
        }
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
