---
id: doc-3
title: 'T0.4 finding: observing agents built by quarkus-langchain4j-agentic'
type: specification
created_date: '2026-10-06 09:36'
updated_date: '2026-10-06 09:36'
---
# T0.4 finding: observing agents built by quarkus-langchain4j-agentic

Spike TASK-9 (2026-10-06). Versions: quarkus-langchain4j 1.14.1, langchain4j-agentic 1.20.2-beta30.
Executable record: `integration-tests/src/test/java/.../it/spike/ListenerHookSpikeTest.java` (5 tests).

## Answer

A hook exists, in two forms with very different coverage.

### 1. CDI `AgentListener` beans (official, but partial)

`AgenticRecorder.QuarkusAgenticContextConsumer` adds every CDI `AgentListener` bean to each agent built through
`DeclarativeUtil` (`agentBuilder.listener(listener)`), i.e. **AI agents only**.

| Seen | Not seen |
| --- | --- |
| before/after of every AI agent (incl. each loop iteration and parallel branch), tool before/after | workflow nodes (sequence, loop, parallel, conditional, supervisor, planner), AgenticScope created/destroyed, human-in-the-loop steps |

`inheritedBySubagents()` makes no difference here (the listener is attached to each AI agent directly) and causes no duplicates.

### 2. Root registration through `InternalAgent` (complete, but internal API)

Quarkus root agent beans are `AbstractQuarkusAgent` instances implementing `dev.langchain4j.agentic.internal.InternalAgent`.
`registerInheritedParentListener(listener)` on the unwrapped root bean (`ClientProxy.unwrap`) composes the listener into the
root and, recursively, into every sub-agent when `inheritedBySubagents()` is true.

Observed for the nested sequence StoryCreator: `scope created`, `before create`, `before draft`, `after draft`,
`before polish`, 3 x (`score`, `edit`), `after polish`, `after create`, `scope destroyed`.
Also seen: parallel branches (on worker threads), supervisor root and its agents with tool calls, the human-in-the-loop
step (`askTraveler`).

### Still invisible with both

- Supervisor / planner decisions (which agent and why): only the resulting invocations. Covered by T0.7 (planner wrapper) / U3.
- Error-handler retries: a missing argument fails before the agent is invoked, so no `onAgentInvocationError`; only the
  successful retry is reported. T2.10 needs another source (e.g. wrapping `@ErrorHandler` or reading the scope).

## Recommendation for T2.4 (DevUiAgentListener)

- Register the Dev UI listener on every **root** agent at dev-mode startup (roots known from build-time root detection,
  T1.3), with `inheritedBySubagents() = true`; instantiate the root beans eagerly like upstream `enableDevModeMonitoring`.
- Do **not** also make it a CDI `AgentListener` bean, or AI-agent callbacks arrive twice.
- `InternalAgent` is an internal API: isolate the call in one class, cover it with the spike test, and propose a public
  hook upstream (refined U5, below). Re-register after hot reload (new root instances).
- Listeners cannot be unregistered: register once per root instance.
- Inject-proxy pitfall: `@ApplicationScoped` listener state must be read through methods, not fields.

## Impact on the backlog

- U5 (TASK-16) as written ("apply CDI AgentListener beans to annotation-built agents") already exists but only reaches AI
  agents. Refine it: a public way to attach an inherited listener to root agents built by the extension (or apply CDI
  listeners with `inheritedBySubagents() = true` at the root). Not blocking thanks to the internal-API workaround.
- No bytecode transform needed (AC 3 of T0.4 does not apply).
- T2.10 (error display) cannot rely on listener callbacks for retries.
