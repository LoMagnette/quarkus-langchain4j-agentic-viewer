---
id: doc-7
title: Upstream proposals U2-U6
type: specification
created_date: '2026-10-17 10:48'
updated_date: '2026-10-21 11:11'
---
# Upstream proposals U2-U6

Drafts, **nothing has been posted**. Versions read: langchain4j-agentic 1.20.2-beta30 (sources), quarkus-langchain4j
1.14.1 (sources), Quarkus 3.33.3.1. Each proposal says what the agentic viewer does today without it: every internal
part it relies on instead is listed in `runtime-dev/.../runs/LangChain4jInternals` (29 entries), which is the strongest
argument for these public hooks. Both projects treat agentic as experimental, so each proposal keeps the API minimal and
additive (default methods, new optional SPI).

Summary:

| | Target | Ask | Viewer workaround today |
|---|---|---|---|
| U2 | langchain4j-agentic | observer of built agentic systems (ServiceLoader) | CDI discovery only (beans); non-bean systems invisible |
| U3 | langchain4j-agentic | listener callback for planner decisions (+ candidates) | replaces the private final `plannerSupplier` by reflection; candidates of patterns read from private fields |
| U4 | langchain4j-agentic | listener callback for pending responses; public `PendingResponse` | swaps the `@HumanInTheLoop` invoker (internal `AgentExecutor`/`AgentInvoker`) |
| U5 | langchain4j-agentic + quarkus-langchain4j | public inherited listener registration; CDI listeners applied at the roots | internal `InternalAgent.registerInheritedParentListener` |
| U6 | quarkus-langchain4j (+ langchain4j-agentic) | an agent shared by two systems must not be re-parented (bug) | builder systems build their own agents |

---

## U2: observe built agentic systems (langchain4j-agentic)

**Problem.** Systems built with `AgenticServices.*Builder().build()` exist only in user code. A tool (a Dev UI, a
tracer) cannot find them, attach a listener or decorate their planner unless they are CDI beans (what the viewer does)
or it rewrites the application bytecode around `build()`.

**Proposal.** A `ServiceLoader` SPI called by every workflow builder (`AbstractServiceBuilder.build(Supplier<Planner>)`):

```java
public interface AgenticSystemObserver {
    /** Before the agent is created: may add listeners and decorate the planner supplier. */
    default Supplier<Planner> beforeBuild(AgentBuildContext context, Supplier<Planner> plannerSupplier) {
        return plannerSupplier;
    }
    /** After creation, with the built agent. */
    default void afterBuild(AgentInstance agent) { }
}
public interface AgentBuildContext {
    String name(); Class<?> agentType(); String serviceType();
    void addListener(AgentListener listener);   // e.g. inheritedBySubagents() = true
}
```

Called in `build(Supplier<Planner>)`, right before `new PlannerBasedInvocationHandler(this, plannerSupplier)` and after
the agent is created. Observers are loaded once (`ServiceLoader.load(AgenticSystemObserver.class)`), none by default:
zero cost for applications without one.

**Root systems.** A builder cannot know whether its result will be a root (nested systems are built first). The
observer gets every workflow build; `agent.parent()` tells roots later (it is set when a parent is built). The viewer
already uses this rule for CDI beans (`BuilderSystems`).

**Test.** A nested builder workflow (a loop inside a sequence): the observer sees both builds; a listener added by it
receives the callbacks of the sub-agents; a decorated planner supplier is used.

---

## U3: planner decisions on AgentListener (langchain4j-agentic)

**Problem.** Supervisors, custom planners and the patterns planners decide at runtime which agents run. A listener sees
the invocations but not the decisions (why an agent was chosen, when the planner said done), nor the candidates a
planner considered (blackboard: the agents that could fire; P2P: the ready agents).

**Proposal.** A default method on `AgentListener`, called by the planner loop
(`PlannerBasedInvocationHandler.PlannerLoop.loop`) after `firstAction` / `nextAction`:

```java
default void onPlannerDecision(PlannerDecision decision) { }

public record PlannerDecision(AgenticScope agenticScope, AgentInstance plannerAgent, Action action,
        List<AgentInstance> candidates) { }
```

`candidates` comes from an optional default method on `Planner`, overridden by planners that know (blackboard,
P2P): `default List<AgentInstance> candidates(PlanningContext context) { return List.of(); }`. Built-in workflow
planners (sequence, loop, parallel, conditional) need not report: a flag on the listener
(`default boolean plannerDecisionsOfWorkflows()`), or reporting only for `topology()` STAR / custom planners.

**Viewer today.** `PlannerDecisions` replaces the private final `plannerSupplier` field of each workflow handler by
reflection with a reporting planner; the P2P state is read from private fields (`maxInvocations`,
`exitCondition`...), the blackboard candidates are those its planner hands its `conflictResolutionStrategy`, a private
field wrapped by reflection (its own `canActivate` changed signature in 1.23 and evaluates activation conditions:
TASK-92).

**Test.** The supervisor (or a custom planner) of a two-agent system: the listener receives call, call, done; a
blackboard planner reports its candidates.

---

## U4: pending human responses on AgentListener (langchain4j-agentic)

**Already there (1.20.2-beta30).** `PendingResponse` / `SuspendedResponse` (package `internal`), returned by a
`HumanInTheLoop` response provider, and the public `AgenticScope.completePendingResponse(id, value)` /
`pendingResponseIds()`: code other than the application can already complete a human step.

**Missing.**
1. Nobody is told that a response is pending, nor what the human is asked: a tool must poll `pendingResponseIds()` and
   cannot show the prompt.
2. `PendingResponse` / `SuspendedResponse` are in `dev.langchain4j.agentic.internal`.
3. A declarative `@HumanInTheLoop` method is called directly, so it must itself create the pending response.

**Proposal.**

```java
// AgentListener
default void onPendingResponse(PendingResponseRequest request) { }

public record PendingResponseRequest(AgenticScope agenticScope, AgentInstance agent, String responseId,
        String description, String outputKey, Map<String, Object> inputs) { }
```

called when a `DeferredResponse` is first read from the scope; move `PendingResponse`, `SuspendedResponse` and
`DeferredResponse` to a public package (`dev.langchain4j.agentic.scope` or `.workflow`). Optionally,
`@HumanInTheLoop(pending = true)` makes the declarative step return a `PendingResponse` named after its output key.

**Viewer today.** `HumanInTheLoopSteps` swaps the invoker of each `@HumanInTheLoop` step (internal `AgentExecutor` and
`AgentInvoker`, `ListenerNotifierUtil`) so a Dev UI run waits for the answer from the page. With U4 it would listen for
`onPendingResponse` and call `completePendingResponse`, for applications whose steps use pending responses.

**Test.** A sequence with a `HumanInTheLoop` returning a `PendingResponse`: the listener receives the request with its
description and key, completes it from another thread, the run ends with the answer.

---

## U5: attach an inherited listener to a built system (langchain4j-agentic, quarkus-langchain4j)

**Problem.** CDI `AgentListener` beans only reach AI agents in quarkus-langchain4j-agentic (finding doc-3): no workflow
nodes, no scope lifecycle, no human steps. The viewer registers its listener on each root with the internal
`InternalAgent.registerInheritedParentListener`.

**Proposal.**
- langchain4j-agentic: a public `AgenticServices.addListener(Object agent, AgentListener listener)` (or
  `AgentInstance.addListener`), inherited by the sub-agents when the listener says so.
- quarkus-langchain4j-agentic: apply the `AgentListener` beans whose `inheritedBySubagents()` is true at the root of
  every declarative system (workflow nodes, scope lifecycle, human steps then reach them).

**Test.** A CDI listener with `inheritedBySubagents() = true` receives the callbacks of a nested declarative workflow
(sequence > loop > agents) and its scope lifecycle.

---

## U6 (bug): an agent shared by two systems is re-parented (quarkus-langchain4j, langchain4j-agentic)

**Description.** Building a system mutates its sub-agents: `AgentExecutor.setParent(parent, index)` calls
`setParent` and `appendId("$" + index)` on the agent itself. quarkus-langchain4j-agentic resolves the sub-agents of a
declarative system to the application-scoped agent beans (`QuarkusSubAgentResolver`), and a builder system given those
beans does the same. When one agent bean is used by two systems, the second build re-parents and renames it: the first
system then reports the wrong parent and ids.

**Steps to reproduce.**
1. Declarative `@SupervisorAgent` `BankSupervisor` with sub-agents `ExchangeAgent`, `WithdrawAgent` (AI agents).
2. Elsewhere: `AgenticServices.sequenceBuilder().subAgents(AgenticServices.supervisorBuilder().name("transact")
   .chatModel(model).subAgents(ClientProxy.unwrap(exchangeAgent)).build()).build()` with the injected beans.
3. Invoke `BankSupervisor` with an `AgentMonitor`.

**Actual.** `IllegalStateException: No ongoing parent invocation found for agent ID: transact$0` (from
`MonitoredExecution.beforeAgentInvocation`); listeners see the agents under the other system; `agentId()` grows
(`draft$0$0`).

**Expected.** An agent can be used by several systems, or building fails fast ("agent already has a parent").

**Suggestions.** Keep the parent and the id suffix on the `AgentExecutor` (per use) instead of the agent; or have
`QuarkusSubAgentResolver` give each parent its own instance; at least document that agents must not be shared.

**Workaround.** Each builder system builds its own agents: `AgenticServices.agentBuilder(MyAgent.class)
.chatModel(chatModel).build()` (the viewer's samples, `integration-tests/.../it/builder/Agents`).
