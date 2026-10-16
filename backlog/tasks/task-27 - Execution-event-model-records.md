---
id: TASK-27
title: Execution event model records
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 13:50'
labels:
  - runtime-dev
  - model
  - live-view
milestone: m-2
dependencies:
  - TASK-17
  - TASK-14
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not a separate spec task; prerequisite of T2.2, T2.4 and T2.5. The spec "Execution event model" table defines the events streamed to the UI. They must exist before the RunManager, listener and broadcaster can emit them, and their shape depends on the AgentMonitor reuse decision.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every event in the spec Execution event model table exists with its key fields (RunStarted ... AwaitingHumanInput)
- [x] #2 Every event carries runId and a timestamp and serializes to JSON
- [x] #3 The model follows the recorded trace-model decision
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime-dev package runtime.dev.events (dev-only, keeps the prod artifact lean): sealed interface ExecutionEvent (runId, timestamp in epoch millis, getType() = simple class name as JSON discriminator, no Jackson annotation needed) and one record per spec event: RunStarted/RunFinished/RunFailed, ScopeCreated/ScopeDestroyed, AgentStarted/AgentFinished/AgentFailed, ToolStarted/ToolFinished, PlannerDecision, ScopeStateChanged, AwaitingHumanInput; helpers ErrorInfo (exception class, message, stack trace text) and TokenUsage.
2. decision-4 semantics: agent events carry nodeId (topology id), invocationId and parentInvocationId (AgentMonitor-style parent correlation), iteration (loop index, null outside loops), token usage on AgentFinished; decision-5: AgentStarted.streaming marks a streaming agent whose output arrives when the stream completes.
3. Tests (deployment test module, like TopologyJsonTest): every event serializes with a default ObjectMapper, carries runId, timestamp and its type; the set of event types matches the spec table.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-14 (decision-4): own event records as in the spec; reuse AgentMonitor semantics for agent ids, parent correlation (agent().parent().agentId()), loop iteration index and token usage (AgentResponse.chatResponse().metadata().tokenUsage()).

runtime-dev package runtime.dev.events: sealed ExecutionEvent (runId, timestamp epoch millis, default getType() = simple class name, serialized as 'type' by Jackson without annotations) permitting RunStarted(systemId, inputs), RunFinished(output, durationMillis), RunFailed(error, durationMillis), ScopeCreated/ScopeDestroyed(memoryId), AgentStarted(nodeId, invocationId, parentInvocationId, agentName, iteration, inputs, streaming), AgentFinished(nodeId, invocationId, output, tokens, durationMillis), AgentFailed(nodeId, invocationId, error, durationMillis), ToolStarted(invocationId of the calling agent, toolCallId, toolName, arguments), ToolFinished(..., result, durationMillis), PlannerDecision(nodeId, invocationId, selectedNodeIds, done, extras), ScopeStateChanged(key, value, truncated, nodeId of the writer), AwaitingHumanInput(nodeId, prompt, expectedKey); helpers ErrorInfo(exceptionClass, message, stackTrace; ErrorInfo.of(Throwable)) and TokenUsage(input, output, total). decision-4 (own events with AgentMonitor semantics) -> invocation ids, parent invocation correlation, loop iteration index, token usage; decision-5 -> AgentStarted.streaming (output arrives with AgentFinished when the stream completes). Note: the decision-4 record in backlog/decisions has only its title filled in; its content lives in TASK-14's notes. Tests: ExecutionEventJsonTest (4): the sealed hierarchy equals the spec table, every event serializes with runId, timestamp and type (default ObjectMapper), exact AgentStarted JSON and token usage, ErrorInfo trace.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The execution event model exists in runtime-dev as a sealed ExecutionEvent hierarchy with one record per spec event, each with run id, timestamp and a JSON type discriminator, following the AgentMonitor-based trace decision (invocation ids, parent correlation, iteration, tokens) and the streaming decision. Verified by JSON serialization tests covering every event.
<!-- SECTION:FINAL_SUMMARY:END -->
