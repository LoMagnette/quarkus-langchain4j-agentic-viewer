---
id: TASK-29.2
title: 'T2.2b: Bounded run history, listRuns/getRun and cancelRun'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:48'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies:
  - TASK-29.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-29
priority: medium
type: feature
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.2 (F4.9, F6.3). Past runs are reopened and replayed from the history; history must be bounded. Cancel is best effort since agents may be inside a blocking model call.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 History keeps the last N runs per system (default 20) with their full event trace and drops the oldest beyond that
- [x] #2 listRuns(systemId) returns run summaries and getRun(runId) the full trace
- [x] #3 cancelRun(runId) attempts cancellation, returns whether it was applied, and the run ends with a cancelled status
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunStatus CANCELLED; Run ends once (first end wins), keeps its task future, end time and duration.
2. RunManager: history per system bounded by quarkus.langchain4j.agentic.dev-ui.history-size (default 20, ConfigProvider like the payload size; config root in T6.1): starting a run evicts the oldest ended runs beyond the limit (running runs are never dropped). listRuns(systemId) -> RunSummary(runId, systemId, status, startedAt, durationMillis, inputs) newest first; getRun(runId) -> RunTrace(summary, events). cancelRun(runId): if running, ends it as CANCELLED with RunFailed(CancellationException 'Run cancelled'), interrupts its thread (future.cancel(true)) and returns true; whatever the system returns later is ignored; false for unknown or ended runs.
3. JSON-RPC listRuns, getRun, cancelRun.
4. Tests: unit tests (bound and eviction order, running not evicted, summaries, trace, cancel of a blocked run -> CANCELLED and no RunFinished afterwards, cancel of an ended/unknown run -> false); dev-mode JSON-RPC listRuns/getRun/cancelRun.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RunStatus CANCELLED. Run: first end wins (claimEnd, AtomicBoolean) with its duration, keeps its task Future. RunManager: history per system (run ids oldest first) bounded by quarkus.langchain4j.agentic.dev-ui.history-size (default 20, ConfigProvider; constructor parameter for tests); starting a run evicts the oldest ended runs beyond the limit with their trace, running runs are never evicted. listRuns(systemId) -> RunSummary(runId, systemId, status, startedAt, durationMillis (null while running), inputs) newest first; trace(runId) -> RunTrace(summary, events); cancel(runId): claims the end, emits RunFailed(CancellationException 'Run cancelled'), ends CANCELLED, interrupts the run thread (future.cancel(true)); a later result or failure of the system is ignored and events of an ended run are dropped; false for unknown or ended runs. JSON-RPC listRuns, getRun (unknown -> error 'Unknown run'), cancelRun. Tests: RunHistoryTest (4: last 3 per system newest first with eviction of the oldest traces, running run kept and evicted after its end, trace, cancel of a blocked run -> CANCELLED with RunFailed and no late RunFinished, cancel of ended/unknown -> false); AgenticSystemsDevModeTest.runHistory (listRuns, getRun, cancelRun false on an ended run, getRun unknown error over the raw websocket). Validation: deployment 98, integration-tests 51.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Runs are kept in a bounded history per system (last 20 by default, oldest ended runs dropped) with their full trace; listRuns, getRun and cancelRun are available over JSON-RPC, and a cancelled run ends at once with a cancelled status. Verified by unit tests and dev-mode JSON-RPC tests.
<!-- SECTION:FINAL_SUMMARY:END -->
