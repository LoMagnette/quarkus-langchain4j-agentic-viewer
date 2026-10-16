---
id: TASK-31
title: 'T2.4: DevUiAgentListener attached to declarative agents'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:05'
labels:
  - runtime-dev
  - live-view
milestone: m-2
dependencies:
  - TASK-9
  - TASK-27
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.4 (F6.2). Depends on the hook found by the T0.4 spike, or on U5 if the spike found none. The listener is an `AgentListener` with `inheritedBySubagents() = true` that maps each callback to an execution event. Listener callbacks run on agent threads, so they must never block.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The listener is attached to every declarative root system and inherited by sub-agents
- [x] #2 All callbacks (scope created/destroyed, agent before/after/error, tool before/after) are received and mapped to events for every sample system
- [x] #3 Callbacks only enqueue the event and return; a test with a slow subscriber shows no delay in agent execution
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunManager: events go through a single dispatcher thread (FIFO, per-run order kept): emit() enqueues and returns; the dispatcher records the event on the run and calls the listeners; run.end() is enqueued after RunFinished/RunFailed so a completed run has all its events. The run thread marks its run (thread-local) during the invocation.
2. DevUiAgentListener (AgentListener, inheritedBySubagents): afterAgenticScopeCreated maps the scope (identity) to the run of the current thread; other callbacks find the run through their scope (works on parallel worker threads) and are ignored outside Dev UI runs. Callbacks build ScopeCreated/ScopeDestroyed, AgentStarted/Finished/Failed (inputs copied, tokens from the chat response, streaming when the output type is a stream), ToolStarted/Finished and enqueue them; nothing blocks, nothing is stringified.
3. Node ids from the AgentInstance chain: root = system id (root type -> system), each level = simple class name of the sub-agent with ~n by position among same-named siblings (TopologyBuilder rules). Invocation ids: per-thread stack, parent = the parent's open invocation (thread stack, else the latest open invocation of the parent instance for parallel branches). Iterations and mapper item indexes are T2.6.
4. RootListeners: registers the listener on every declarative root bean through InternalAgent.registerInheritedParentListener (internal API isolated in this class, as the T0.4 spike recommends), at dev-mode startup (StartupEvent observer bean, registered dev-only).
5. Tests (integration-tests @QuarkusTest, attaching the listener to the sample roots): every sample system emits scope, agent and tool events with run id and node ids from its topology; a slow subscriber (sleep per event) does not slow the run (run duration well below events x sleep) and still receives all events.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design input from TASK-9 (doc-3): register the Dev UI listener on each root agent instance at dev-mode startup (InternalAgent.registerInheritedParentListener, inheritedBySubagents = true); do not make it a CDI AgentListener bean (duplicate AI-agent callbacks). Re-register after hot reload.

From TASK-15 (decision-5): a streaming agent output can be a StreamingResponse/TokenStream whose toString() blocks until the stream completes. Never stringify or truncate such outputs inside the listener callback; emit the finished event when the delayed response completes.

RunManager: emit() now queues the event on a single event thread ('agentic-viewer-events-N') which records it on the run and calls the listeners in order; run.end() is queued after RunFinished/RunFailed; the run thread exposes its run (thread-local, RunManager.currentRun()). DevUiAgentListener (runs package, inheritedBySubagents): afterAgenticScopeCreated maps the scope (identity map) to the current run, only for runs of its own manager (listeners stay on the roots for the app's life; the run marker is static); scope/agent/tool callbacks find their run through the scope and are ignored otherwise; ScopeCreated/Destroyed(memoryId), AgentStarted (nodeId, invocation id 'inv-N', parent invocation, inputs copy, streaming when the output type is TokenStream/Multi/Flow.Publisher), AgentFinished (output object, tokens from ChatResponse metadata, duration), AgentFailed (ErrorInfo), ToolStarted/Finished (calling invocation, tool call id, name, arguments, result, duration). Agents are matched by agentId() (AgentMonitor semantics): identity failed for conditional branches and tool callbacks (different AgentInstance objects for the same agent). Node ids from the AgentInstance parent chain and the position among same-named siblings (TopologyBuilder rules). RootListeners: InternalAgent.registerInheritedParentListener on each unwrapped root bean (only internal API use); RunObservation (@Observes StartupEvent, dev-only bean) attaches at startup. In the deployment dev-mode fixture the root bean cannot be created (upstream: no provider extension, no ChatModel wiring), which logs 'cannot observe' as intended; the sample app in dev mode attaches to every root without warning. Tests: integration-tests SampleRunEventsTest (3): every sample run has RunStarted first, scope created/destroyed, root AgentStarted with the system id, node ids from its topology, every invocation finished, parents set; StoryCreator node sequence (draft, loop, 3 x score/edit); BankSupervisor tool events exchange/withdraw/credit with calling invocation; a subscriber sleeping 150 ms per event: run duration < events x 150 / 2 and all events received in order. Validation: deployment 81, integration-tests 45, all pass. Left to T2.6: loop iteration index, mapper item index, same sub-agent twice, dedicated parallel correctness tests.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A DevUiAgentListener attached at dev-mode startup to every declarative root (inherited by sub-agents) turns scope, agent and tool callbacks of Dev UI runs into execution events with run id, topology node id and invocation ids; callbacks only queue events, delivered in order by a dedicated event thread. Verified by running every sample with the listener attached, including a slow-subscriber test.
<!-- SECTION:FINAL_SUMMARY:END -->
