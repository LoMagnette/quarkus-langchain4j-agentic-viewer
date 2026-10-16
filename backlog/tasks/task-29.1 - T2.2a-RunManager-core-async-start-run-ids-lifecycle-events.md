---
id: TASK-29.1
title: 'T2.2a: RunManager core: async start, run ids, lifecycle events'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 13:59'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-29
priority: high
type: feature
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.2. startRun must return at once with a run id; the run executes on a worker thread and its lifecycle is reported as events.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 startRun(systemId, inputs) returns a runId immediately and executes the run on a worker thread
- [x] #2 RunStarted, RunFinished (output, duration) and RunFailed (error, duration) events are emitted
- [x] #3 Two concurrent runs of the same system are tracked separately
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime-dev runs package: Run (id, systemId, start time, status RUNNING/SUCCEEDED/FAILED, output, thread-safe event list, completion future), RunInvoker (invoke(AgenticSystem, inputs)).
2. RunManager (@ApplicationScoped, registered dev-only; also constructible without CDI from a system lookup and an invoker, so tests and integration tests can drive it): startRun(systemId, inputs) checks the system, creates the run, emits RunStarted and submits the invocation to its own executor (daemon threads carrying the application class loader) and returns the run id at once; emits RunFinished(output, duration) or RunFailed(error, duration); listeners receive every event (for the T2.5 broadcaster); shut down on @PreDestroy.
3. JSON-RPC startRun(systemId, inputs) -> runId.
4. Tests: RunManagerTest with a blocking fake invoker (returns before the run ends, events, failure, two concurrent runs of one system tracked separately, unknown system); dev-mode JSON-RPC startRun.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
runtime-dev runs package: Run (id, systemId, inputs, startedAt, status RUNNING/SUCCEEDED/FAILED, output, CopyOnWrite event list, completion future), RunStatus, RunInvoker, RunManager (@ApplicationScoped via the dev-only AdditionalBeanBuildItem with the registry; @Inject constructor from AgenticSystemRegistry + CdiRunInvoker, public constructor from a system lookup and an invoker for tests): startRun checks the system (IllegalArgumentException 'Unknown agentic system'), emits RunStarted, submits to a cached pool of daemon threads 'agentic-viewer-run-N' carrying the class loader of the creator, returns the id; RunFinished(output, duration) or RunFailed(ErrorInfo of the unwrapped cause, duration); addListener for the T2.5 broadcaster (exceptions logged, never propagated); executor shut down on @PreDestroy. JSON-RPC startRun(systemId, inputs) -> run id. Tests: RunManagerTest (4: returns while the run is blocked, run thread name, events and output, failure event, two overlapping runs of one system with separate ids/events and listeners seeing all, unknown system); AgenticSystemsDevModeTest startRun returns a run id, unknown system is a JSON-RPC error. Validation: deployment 81 tests, integration-tests 42 (twice), patterns profile 48.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RunManager starts runs of agentic systems on its own worker threads, returns the run id at once, reports RunStarted/RunFinished/RunFailed and keeps concurrent runs apart; startRun is exposed over JSON-RPC. Verified by unit tests with a blocking fake invoker and dev-mode JSON-RPC tests.
<!-- SECTION:FINAL_SUMMARY:END -->
