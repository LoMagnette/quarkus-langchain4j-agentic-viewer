package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.ref.WeakReference;
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
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorHandled;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeDestroyed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.TokenUsage;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeIds;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Turns the agentic callbacks of runs into execution events. Attached to every root agent and inherited by its
 * sub-agents ({@link RootListeners}). Callbacks run on the agent threads: they only build the event and queue it
 * ({@link RunManager#emit}), never block, and never stringify outputs (a streaming response would block).
 * <p>
 * A run is found through its AgenticScope: the scope is created, or reused (a memory id), by the root call, which
 * tells the run; later callbacks, on any thread, carry the scope. The root call of a Dev UI run is on its run thread,
 * which knows the run. Any other root call of a known system is an application run (when the manager records them),
 * ended when that call returns or fails, unless it comes from an agent of a run in progress on the same thread (another
 * system called by a step), which it is part of. A scope belongs to a run until the run ends: a later call reusing it is
 * another run. Callbacks of invocations of no run are ignored.
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
     * Invocations in progress per run and agent id ({@link #openKey}), for the parent of a branch running on another
     * thread. Agents are matched by {@link AgentInstance#agentId()} (as AgentMonitor does): callbacks may hand different
     * objects for the same agent.
     */
    private final Map<String, Deque<Invocation>> openInvocations = new HashMap<>();
    private final Map<String, Long> toolStarts = new ConcurrentHashMap<>();

    /**
     * The scope the current thread just created, with no Dev UI run: the root call that follows may start an application
     * run, which then tells its creation (weakly: a scope created for anything else is not kept).
     */
    private final ThreadLocal<WeakReference<AgenticScope>> createdScope = new ThreadLocal<>();

    /** One error observer per run, so the scope's handler is wrapped once. */
    private final Map<String, ErrorHandlers.Observer> errorObservers = new ConcurrentHashMap<>();

    /** Node kinds of each system's topology, by node id. */
    private final Map<String, Map<String, AgentNode>> nodesBySystem = new ConcurrentHashMap<>();

    /**
     * An invocation in progress, with what its children need: the loop counter of a loop, the items of a parallel
     * mapper and the indexes already given to its children.
     */
    private static final class Invocation {
        final Run run;
        final String agentId;
        final String id;
        final long startNanos;
        final String nodeId;
        final Integer iteration;
        final Integer itemIndex;
        /** Whether it is the root call of an application run: the run ends with it. */
        boolean applicationRoot;
        int loopCounter = -1;
        List<Object> items;
        final Set<Integer> takenItems = new HashSet<>();
        /** Starts of children per node id, to tell apart one agent listed several times by its parent. */
        final Map<String, Integer> childStarts = new HashMap<>();

        Invocation(Run run, String agentId, String id, String nodeId, Integer iteration, Integer itemIndex) {
            this.run = run;
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

    private final PatternAdapters patternAdapters;

    public DevUiAgentListener(RunManager runManager) {
        this(runManager, PatternAdapters.none());
    }

    /**
     * @param patternAdapters what is known about the planners of langchain4j-agentic-patterns (none without it)
     */
    public DevUiAgentListener(RunManager runManager, PatternAdapters patternAdapters) {
        this.runManager = runManager;
        this.patternAdapters = patternAdapters;
        runManager.addListener(this::forgetEndedRun);
    }

    /**
     * A run ended: its scopes no longer belong to it. A scope with a memory id outlives the root call (langchain4j only
     * evicts ephemeral ones), and a later call reusing it, from the application or another run, is not this run.
     */
    private void forgetEndedRun(ExecutionEvent event) {
        if (!(event instanceof RunFinished) && !(event instanceof RunFailed)) {
            return;
        }
        String runId = event.runId();
        synchronized (runsByScope) {
            runsByScope.values().removeIf(run -> run.id().equals(runId));
        }
        errorObservers.remove(runId);
        toolStarts.keySet().removeIf(key -> key.startsWith(runId + "/"));
        synchronized (openInvocations) {
            // e.g. agents of a cancelled run, or that died with an Error (langchain4j reports no error then)
            openInvocations.keySet().removeIf(key -> key.startsWith(runId + "|"));
        }
    }

    PatternAdapters patternAdapters() {
        return patternAdapters;
    }

    /** @return whether its manager was shut down (it then leaves the hooks of the roots it was attached to) */
    boolean isClosed() {
        return runManager.isShutdown();
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        Optional<Run> devUiRun = currentRun();
        if (devUiRun.isPresent()) {
            Run run = devUiRun.get();
            runsByScope.put(scope, run);
            runManager.emit(run, new ScopeCreated(run.id(), now(), String.valueOf(scope.memoryId())));
        } else {
            createdScope.set(new WeakReference<>(scope));
        }
    }

    /**
     * The run of the current thread if it is a run thread of this listener's manager: listeners stay attached to the
     * roots for the life of the application.
     */
    private Optional<Run> currentRun() {
        return RunManager.currentRun().filter(run -> runManager.run(run.id()).orElse(null) == run && !run.ended());
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
        beforeAgentInvocation(request, null);
    }

    /**
     * @param systemId the system of the root this listener is attached to through ({@link DevUiListeners}), whose
     *        calls outside a Dev UI run are application runs; null for none
     */
    void beforeAgentInvocation(AgentRequest request, String systemId) {
        boolean applicationRoot = request.agent().parent() == null && rootCall(request, systemId);
        Run run = runOf(request.agenticScope());
        if (run == null) {
            return;
        }
        // the root call sets the scope's error handler: keep it observed (cheap when it already is)
        ErrorHandlers.observe(request.agenticScope(), errorObservers.computeIfAbsent(run.id(), id -> errorObserver(run)));
        AgentInstance agent = request.agent();
        // what this thread still holds of other runs is stale: their agents ended without a callback (an Error)
        threadInvocations.get().removeIf(invocation -> invocation.run != run);
        Invocation parent = openInvocation(run, agent.parent()).orElse(null);
        if (parent == null && agent.parent() == null) {
            // another system called from an agent of the run (e.g. a step delegating to a builder system): under that
            // agent, not as the run's root (its nodes are not in the graph, the timeline nests them)
            parent = threadInvocations.get().peekLast();
        }
        String nodeId = parent == null ? nodeId(run, agent) : childNodeId(run, parent, agent);
        Map<String, Object> inputs = copy(request.inputs());
        Integer iteration = parent == null ? null : parent.iteration;
        Integer itemIndex = parent == null ? null : parent.itemIndex;
        AgentNode parentNode = parent == null ? null : node(run, parent.nodeId);
        if (parentNode != null && parentNode.kind() == NodeKind.LOOP) {
            synchronized (parent) {
                // an iteration starts with the first agent of the loop body
                if (parentNode.children().isEmpty() || parentNode.children().getFirst().id().equals(nodeId)
                        || parent.loopCounter < 0) {
                    parent.loopCounter++;
                }
                iteration = parent.loopCounter;
            }
        } else if (parentNode != null && parentNode.kind() == NodeKind.PARALLEL_MAPPER) {
            itemIndex = itemIndex(parent, request.agentName(), inputs);
        }
        Invocation invocation = new Invocation(run, agent.agentId(), "inv-" + INVOCATIONS.incrementAndGet(), nodeId,
                iteration, itemIndex);
        invocation.applicationRoot = applicationRoot;
        AgentNode node = node(run, nodeId);
        if (node != null && node.kind() == NodeKind.PARALLEL_MAPPER) {
            invocation.items = items(inputs);
        }
        threadInvocations.get().addLast(invocation);
        synchronized (openInvocations) {
            openInvocations.computeIfAbsent(openKey(run, agent.agentId()), a -> new ArrayDeque<>()).addLast(invocation);
        }
        runManager.emit(run, new AgentStarted(run.id(), now(), nodeId, invocation.id(), parent == null ? null : parent.id,
                request.agentName(), iteration, itemIndex, inputs, isStreaming(agent)));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Run run = runOf(response.agenticScope());
        Invocation invocation = run == null ? null : close(run, response.agent());
        if (invocation != null) {
            long now = now();
            runManager.emit(run, new AgentFinished(run.id(), now, invocation.nodeId, invocation.id(),
                    response.output(), tokens(response.chatResponse()), elapsedMillis(invocation)));
            String outputKey = response.agent().outputKey();
            if (outputKey != null && !outputKey.isBlank()) {
                // the output is what the agent writes under its key (bounded later, on the event thread)
                runManager.emit(run, new ScopeStateChanged(run.id(), now, outputKey, response.output(), false,
                        invocation.nodeId));
            }
            if (invocation.applicationRoot) {
                runManager.endApplicationRun(run, response.output(), null);
            }
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        Run run = runOf(error.agenticScope());
        Invocation invocation = run == null ? null : close(run, error.agent());
        if (invocation != null) {
            runManager.emit(run, new AgentFailed(run.id(), now(), invocation.nodeId, invocation.id(),
                    ErrorInfo.of(error.error()), elapsedMillis(invocation)));
            if (invocation.applicationRoot) {
                runManager.endApplicationRun(run, null, error.error());
            }
        }
    }

    /**
     * A root call (an agent without parent): tells the run its scope belongs to. Its scope may be reused (same memory
     * id) without afterAgenticScopeCreated.
     *
     * @return whether it starts an application run
     */
    private boolean rootCall(AgentRequest request, String systemId) {
        AgenticScope scope = request.agenticScope();
        WeakReference<AgenticScope> created = createdScope.get();
        createdScope.remove();
        Optional<Run> devUiRun = currentRun();
        if (devUiRun.isPresent()) {
            runsByScope.put(scope, devUiRun.get());
            return false;
        }
        Run known = runOf(scope);
        if (known != null && !known.endClaimed()) {
            return false; // a scope of a run in progress (a memory id called again meanwhile): part of that run
        }
        if (known != null) {
            runsByScope.remove(scope); // the scope of an ended run, not forgotten yet: this call is another run
        }
        if (RunManager.currentRun().isPresent()) {
            return false; // a Dev UI run of another manager
        }
        Invocation enclosing = openOnThisThread();
        if (enclosing != null) {
            // another system called from an agent of a run in progress (e.g. a step delegating to a builder system)
            runsByScope.put(scope, enclosing.run);
            return false;
        }
        Run run = runManager.startApplicationRun(systemId, request.inputs()).orElse(null);
        if (run == null) {
            return false;
        }
        runsByScope.put(scope, run);
        if (created != null && created.get() == scope) {
            runManager.emit(run, new ScopeCreated(run.id(), now(), String.valueOf(scope.memoryId())));
        }
        return true;
    }

    /** @return the innermost invocation in progress on this thread of a run not ended, null for none */
    private Invocation openOnThisThread() {
        for (var it = threadInvocations.get().descendingIterator(); it.hasNext();) {
            Invocation invocation = it.next();
            if (!invocation.run.endClaimed()) {
                return invocation;
            }
        }
        return null;
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
        Run run = runOf(execution.agenticScope());
        if (run == null) {
            return;
        }
        ToolExecutionRequest request = execution.toolExecution().request();
        toolStarts.put(toolKey(run, request), System.nanoTime());
        runManager.emit(run, new ToolStarted(run.id(), now(), callingInvocation(run, execution.agentInstance()), request.id(),
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
        runManager.emit(run, new ToolFinished(run.id(), now(), callingInvocation(run, execution.agentInstance()), request.id(),
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
     * Reports what a planner of a run decided ({@link PlannerDecisions}): on the thread running the planner's workflow,
     * whose invocation is open. The agents chosen map to the workflow node's children by their position among the
     * planner's sub-agents (declaration order, as in the topology).
     */
    void plannerDecision(Run run, AgentInstance plannerAgent, List<AgentInstance> subagents, Action action,
            Map<String, Object> patternExtras, Map<String, List<AgentInstance>> patternAgents) {
        if (action.getClass().getSimpleName().equals(LangChain4jInternals.NO_OP_ACTION)) {
            return; // nothing decided
        }
        Invocation invocation = openInvocation(run, plannerAgent).orElse(null);
        String nodeId = invocation == null ? nodeId(run, plannerAgent) : invocation.nodeId;
        AgentNode node = node(run, nodeId);
        List<String> selected = new ArrayList<>();
        if (action instanceof Action.AgentCallAction call) {
            call.agentsToCall().forEach(chosen -> selected.add(childNodeId(nodeId, node, subagents, chosen)));
        }
        Map<String, Object> extras = new HashMap<>(patternExtras == null ? Map.of() : patternExtras);
        // agents a pattern adapter tells about (e.g. the path a GOAP planner computed), as node ids
        (patternAgents == null ? Map.<String, List<AgentInstance>> of() : patternAgents).forEach((name, agents) -> extras
                .put(name, agents.stream().map(agent -> childNodeId(nodeId, node, subagents, agent)).toList()));
        extras.put("action", action.isSuspended() ? "suspend"
                : action instanceof Action.AgentCallAction ? "call"
                        : action.getClass().getSimpleName().equals(LangChain4jInternals.DONE_WITH_RESULT_ACTION) ? "doneWithResult" : "done");
        if (action.getClass().getSimpleName().equals(LangChain4jInternals.DONE_WITH_RESULT_ACTION) && action.result() != null) {
            extras.put("result", action.result());
        }
        runManager.emit(run, new PlannerDecision(run.id(), now(), nodeId, invocation == null ? null : invocation.id(),
                selected, action.isDone(), extras));
    }

    /** The node of a planner's sub-agent: the child at its position among the planner's sub-agents. */
    private static String childNodeId(String plannerNodeId, AgentNode plannerNode, List<AgentInstance> subagents,
            AgentInstance agent) {
        int index = -1;
        for (int i = 0; i < subagents.size() && index < 0; i++) {
            if (subagents.get(i).agentId().equals(agent.agentId())) {
                index = i;
            }
        }
        return plannerNode != null && index >= 0 && index < plannerNode.children().size()
                ? plannerNode.children().get(index).id()
                : NodeIds.child(plannerNodeId, simpleName(agent.type()), 1);
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
        Invocation invocation = openInvocation(run, step).orElse(null);
        AwaitingHumanInput waiting = new AwaitingHumanInput(run.id(), now(),
                invocation == null ? nodeId(run, step) : invocation.nodeId, invocation == null ? null : invocation.id(),
                prompt == null || prompt.isBlank() ? null : prompt, expectedKey,
                valueType == null ? null : valueType.getTypeName(), copy(inputs));
        return runManager.awaitHumanInput(run, waiting, valueType);
    }

    /**
     * The innermost invocation of this agent in progress in a run: on this thread first, else on any thread. On this
     * thread, the invocation of a parallel mapper item ({@code <agent id>_<index>}, langchain4j MapperAgentInvoker)
     * is one of its agent: the sub-agents of an item that is a workflow name the workflow itself as their parent.
     */
    private Optional<Invocation> openInvocation(Run run, AgentInstance agent) {
        if (agent == null) {
            return Optional.empty();
        }
        Deque<Invocation> local = threadInvocations.get();
        for (var it = local.descendingIterator(); it.hasNext();) {
            Invocation invocation = it.next();
            if (invocation.run == run && (invocation.agentId().equals(agent.agentId())
                    || isItemOf(invocation.agentId(), agent.agentId()))) {
                return Optional.of(invocation);
            }
        }
        synchronized (openInvocations) {
            Deque<Invocation> open = openInvocations.get(openKey(run, agent.agentId()));
            return open == null ? Optional.empty() : Optional.ofNullable(open.peekLast());
        }
    }

    private static boolean isItemOf(String itemAgentId, String agentId) {
        return itemAgentId.length() > agentId.length() + 1 && itemAgentId.startsWith(agentId)
                && itemAgentId.charAt(agentId.length()) == '_'
                && itemAgentId.substring(agentId.length() + 1).chars().allMatch(Character::isDigit);
    }

    private static String openKey(Run run, String agentId) {
        return run.id() + "|" + agentId;
    }

    private Invocation close(Run run, AgentInstance agent) {
        Deque<Invocation> local = threadInvocations.get();
        Invocation closed = null;
        for (var it = local.descendingIterator(); it.hasNext();) {
            Invocation invocation = it.next();
            if (invocation.run == run && invocation.agentId().equals(agent.agentId())) {
                it.remove();
                closed = invocation;
                break;
            }
        }
        if (closed != null) {
            String key = openKey(run, agent.agentId());
            synchronized (openInvocations) {
                Deque<Invocation> open = openInvocations.get(key);
                if (open != null) {
                    open.remove(closed);
                    if (open.isEmpty()) {
                        openInvocations.remove(key);
                    }
                }
            }
        }
        return closed;
    }

    private String callingInvocation(Run run, AgentInstance agent) {
        return openInvocation(run, agent).map(Invocation::id).orElse(null);
    }

    /**
     * The topology node of a sub-agent invocation: a child of the parent invocation's node named after the sub-agent's
     * class (TopologyBuilder rules), {@code ~n} for the n-th sub-agent of that class of its parent agent. The same agent
     * listed several times (one agent bean in a declarative system) cannot be told apart: the k-th start under the
     * parent invocation takes the k-th of its positions, cycling for loops. An id the topology does not have falls back
     * to the plain class name.
     */
    private String childNodeId(Run run, Invocation parent, AgentInstance agent) {
        String segment = simpleName(agent.type());
        String base = NodeIds.child(parent.nodeId, segment, 1);
        List<Integer> occurrences = occurrences(agent);
        String id;
        if (occurrences.size() <= 1) {
            id = NodeIds.child(parent.nodeId, segment, occurrences.isEmpty() ? 1 : occurrences.getFirst());
        } else {
            synchronized (parent) {
                int start = parent.childStarts.merge(base, 1, Integer::sum) - 1;
                id = NodeIds.child(parent.nodeId, segment, occurrences.get(start % occurrences.size()));
            }
        }
        AgentNode parentNode = node(run, parent.nodeId);
        return parentNode == null || parentNode.children().stream().anyMatch(child -> child.id().equals(id)) ? id : base;
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
        List<Integer> occurrences = occurrences(agent);
        return NodeIds.child(nodeId(run, parent), simpleName(agent.type()), occurrences.isEmpty() ? 1 : occurrences.getFirst());
    }

    /**
     * The positions of an agent among its parent's sub-agents of its class (1 for the first), found by agent id (a
     * parallel mapper item is one of its agent). Several when the parent lists the same agent several times; none
     * without a parent or position.
     */
    private static List<Integer> occurrences(AgentInstance agent) {
        AgentInstance parent = agent.parent();
        if (parent == null) {
            return List.of();
        }
        String segment = simpleName(agent.type());
        List<Integer> occurrences = new ArrayList<>();
        int occurrence = 0;
        for (AgentInstance sibling : parent.subagents()) {
            if (simpleName(sibling.type()).equals(segment)) {
                occurrence++;
            }
            if (sibling.agentId().equals(agent.agentId()) || isItemOf(agent.agentId(), sibling.agentId())) {
                occurrences.add(occurrence);
            }
        }
        return occurrences;
    }

    private static String simpleName(Class<?> type) {
        return NodeIds.simpleName(type);
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
        return run.id() + "/" + (request.id() != null ? request.id() : request.name() + "@" + Thread.currentThread().threadId());
    }

    private static long elapsedMillis(Invocation invocation) {
        return (System.nanoTime() - invocation.startNanos()) / 1_000_000;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
