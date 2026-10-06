---
id: doc-4
title: 'T0.7 finding: observing planner decisions'
type: specification
created_date: '2026-10-06 09:41'
updated_date: '2026-10-06 09:41'
---
# T0.7 finding: observing planner decisions

Spike TASK-12 (2026-10-06). Versions: quarkus-langchain4j 1.14.1, langchain4j-agentic 1.20.2-beta30, Java 25.
Executable record: `integration-tests/src/test/java/.../it/spike/PlannerWrapSpikeTest.java` (3 tests).

## How planners are held

Every planner-based agent (sequence, parallel, loop, conditional, supervisor, `@PlannerAgent`) is a
`PlannerBasedInvocationHandler` holding a **private final `Supplier<Planner> plannerSupplier`**, set when the agent is built.
Each invocation calls `plannerSupplier.get()`, so planners are fresh per run. Declarative `@PlannerSupplier` methods
become `() -> invokeStatic(method)` inside `PlannerBasedServiceImpl`.

Under Quarkus the root bean is a generated `<Agent>$$QuarkusAgentImpl` (`AbstractQuarkusAgent`) whose `agent` field is the
`PlannerBasedInvocationHandler` itself (with plain langchain4j it is a JDK proxy around it).

## Answer: before or after `.build()`?

- **Before build: no public hook in the declarative path.** The Quarkus creation hook (`DeclarativeAgentCreationContext`)
  only receives AI-agent builders; planner-based builders are created and built inside `AgenticServices`.
- **After build: only through reflection** on `plannerSupplier` (private final). The prototype replaces it on the root agent
  with a supplier that wraps each fresh planner in a decorator. Works on Java 25 without warning; future JDKs plan to warn
  about and then restrict deep-reflective mutation of final fields (JEP 500), so this needs a guard and a fallback.

## What the decorator sees (prototype)

| Sample | Decisions recorded |
| --- | --- |
| DraftReviewer (custom `@PlannerSupplier` planner) | `first: call [writeDraft]`, `next: call [review]`, `next: call [writeDraft]`, `next: call [review]`, `next: done` |
| BankSupervisor (built-in SupervisorPlanner) | `call [exchange]`, `call [withdraw]`, `call [credit]`, `done with result` |
| StoryWriter (built-in SequentialPlanner) | `call [writeIntro]`, `call [writeAdventure]`, `done` |

The decorator delegates every `Planner` method and records the `Action` returned by `firstAction`/`nextAction`
(agents to call, done, done with result). It also has access to the `PlanningContext` (scope, previous invocation), which
the spec's PlannerDecision event needs. Parallel actions (several agents in one `AgentCallAction`) are visible as one decision,
which the listener alone cannot tell.

## Alternatives considered

| Option | Coverage | Cost / risk |
| --- | --- | --- |
| Reflection on the root's `plannerSupplier` (prototype) | All planner-based roots, built-in and custom | Internal field names; final-field mutation (JEP 500); nested planner agents need a walk of `subagents()` (not verified) |
| Dev-mode bytecode transform of `@PlannerSupplier` methods (wrap the returned planner) | Custom `@PlannerAgent` planners only; built-in planners are created in library code | Build-time transform in deployment (moderate), robust to JDK changes |
| U3 upstream: planner decision callbacks on `AgentListener` | Everything, official | Depends on an upstream change |
| Listener only (T0.4) | Order of invoked agents | No grouping of parallel calls, no `done` decision, no reasons |

## Recommendation for T3.2 (PlannerDecorator)

Use the reflection approach behind a small, isolated `PlannerDecorator` installer applied at dev-mode startup to each root
(same place as the T0.4 root listener registration), guarded so a failure only disables decision events and logs once.
Keep the spike test as a compatibility check, propose U3 upstream, and fall back to listener-derived decisions if the
field becomes unwritable. A `@PlannerSupplier` transform is only worth it if reflection is blocked.
