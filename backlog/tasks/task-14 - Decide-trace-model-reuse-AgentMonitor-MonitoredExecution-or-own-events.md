---
id: TASK-14
title: 'Decide trace model: reuse AgentMonitor/MonitoredExecution or own events'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:12'
labels:
  - decision
  - runtime-dev
milestone: m-0
dependencies:
  - TASK-9
documentation:
  - spec-breakdown.md
priority: medium
type: spike
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Open question from spec-breakdown.md "Testing, risks and open questions". langchain4j-agentic already records invocations in `AgentMonitor` / `MonitoredExecution`. Reusing it would align the waterfall timeline (T2.9) with upstream, but the spec also defines its own execution event model (planner decisions, scope state, HITL). Decide before the event model records are written.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A Backlog decision records whether the Dev UI reuses AgentMonitor/MonitoredExecution, keeps its own events, or combines them
- [x] #2 The decision lists which spec events cannot be derived from AgentMonitor, if any
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision recorded as decision-4 (accepted).

What AgentMonitor / MonitoredExecution offer (langchain4j-agentic 1.20.2-beta30): an AgentListener building, per memory id, a tree of AgentInvocation (agent, inputs, output, start/finish, duration, nested invocations, tool executions, loop iteration index, token usage from the ChatResponse), with ongoing / successful / failed executions and a bounded number of retained sessions. It is a snapshot model queried after the fact.

Spec events that cannot be derived from AgentMonitor:
- ScopeCreated / ScopeDestroyed: the monitor does not implement afterAgenticScopeCreated / beforeAgenticScopeDestroyed.
- ToolStarted: only afterAgentToolExecution is recorded (no tool start).
- PlannerDecision: not observed (needs the T0.7 planner decorator or upstream U3).
- ScopeStateChanged: not tracked (only per-invocation inputs and outputs).
- AwaitingHumanInput: no notion of a pending human step.
- RunStarted / RunFinished / RunFailed with a run id: the monitor groups by memory id, not by Dev UI run.
Also not provided: push notifications (the live view needs a stream, the monitor can only be polled), payload truncation (it keeps full objects, conflicting with F6.3), error-handler retries (invisible to listeners, T0.4).

Derivable / reusable: AgentStarted / AgentFinished / AgentFailed (agent id, inputs, output, error), nesting through agent().parent().agentId() correlation, loop iteration index (count of same agent id under a LOOP parent), token usage (AgentResponse.chatResponse().metadata().tokenUsage()), durations.

Decision: the Dev UI keeps its own execution event model (spec "Execution event model"), produced by the root-registered DevUiAgentListener (T0.4) and the PlannerDecorator (T0.7), and reuses AgentMonitor's semantics for ids, parent correlation, iteration index and token usage. AgentMonitor is used as a test oracle: T2.9 already requires the waterfall to match AgentMonitor output for the same run.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Decided (decision-4) to keep an own execution event model, reusing AgentMonitor semantics (agent ids, parent correlation, loop iteration index, token usage) and using AgentMonitor only as a test oracle. AgentMonitor cannot provide scope lifecycle, tool start, planner decisions, scope state changes, HITL waits, run ids, push notifications or truncation; the list is in the notes.
<!-- SECTION:FINAL_SUMMARY:END -->
