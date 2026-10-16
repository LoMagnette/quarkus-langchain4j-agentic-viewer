---
id: TASK-32
title: 'T2.5: Event broadcaster and streamRun with replay'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:09'
labels:
  - runtime-dev
  - json-rpc
  - live-view
milestone: m-2
dependencies:
  - TASK-31
  - TASK-29.1
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.5. The UI subscribes after startRun returns, by which time events may already have been emitted, so the stream must replay them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 streamRun(runId) returns a Multi<ExecutionEvent> that replays already emitted events, then streams live ones
- [x] #2 A subscriber joining after the run finished still sees the full run
- [x] #3 The stream completes when the run ends
- [x] #4 The broadcaster is non-blocking
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunManager.stream(runId): Multi<ExecutionEvent> created per subscriber; its setup runs on the event thread like the delivery of events: replays the run's events, then (unless the run has ended) registers the emitter for live events; deliver() emits each event to the run's emitters; run end completes them; a cancelled subscription is removed on the event thread. No gap or duplicate since both run on one thread. Each stream emits downstream on a worker pool (emitOn) with buffering, so a slow consumer never blocks the event thread. Unknown run -> failed Multi.
2. JSON-RPC streamRun(runId) -> Multi<ExecutionEvent>.
3. Tests: unit tests (subscribe after start gets replay + live; subscriber after the end gets the full run and completion; completion when the run ends; a consumer sleeping per item does not delay the run nor a second fast consumer); dev-mode streaming over a raw websocket (startRun then streamRun: events then completion).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RunManager.stream(runId): Multi.createFrom().emitter whose setup is queued on the event thread (Run.subscribe: replay of the events so far, then registration, or completion at once if the run ended); Run.add emits each new event to the registered emitters, Run.end completes them; onTermination unsubscribes on the event thread; emitOn(Mutiny default worker pool) per subscriber so a slow consumer never holds the event thread (buffered). Unknown run -> failed Multi 'Unknown run: <id>'. JSON-RPC streamRun(runId) -> Multi<ExecutionEvent>. Wire format over the Dev UI websocket (probed): a 'Void' ack, then {result: {messageType: SubscriptionMessage, object: <event>}} per event; no completion message reaches the client, so the UI must end on RunFinished/RunFailed (the server Multi completes). Tests: RunStreamTest (4: subscriber joining during the run gets replay then live events without gap or duplicate and completion; subscriber after the end gets the full run and completes; a subscriber sleeping 300 ms per item delays neither the run nor a fast subscriber (< 1 s) and still gets all 7 events; unknown run fails); AgenticSystemsDevModeTest.streamRunReplaysTheRun over a raw websocket (RunStarted then RunFailed of the fixture run, with run id and inputs). Validation: deployment 86, integration-tests 45, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
streamRun returns a Multi of the run's events that replays what was already emitted then follows the run, completes at its end, serves late subscribers the full run, and never blocks on slow consumers. Verified by unit tests with Mutiny test subscribers (including a slow one) and a dev-mode test over the Dev UI websocket.
<!-- SECTION:FINAL_SUMMARY:END -->
