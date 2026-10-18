---
id: TASK-71.16
title: Test isolation and flakiness
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
  - testing
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: chore
ordinal: 122000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The 13 @QuarkusTest sample classes (plus spike tests) each attach another listener, human-step wrapper and planner wrapper to the same agent beans for the life of the app, so late test classes run through up to 13 nested wrappers and results depend on class order; their RunManagers are never shut down. RunShutdownTest compares agentic-viewer thread counts that other classes (RunHistoryTest, RunManagerTest, RunStreamTest never shut down) influence. AgenticSystemsPageUiTest pulse/afterglow checks and LargeSystemUiTest budgets are timing-bound; RunStreamTest and RunHistoryTest use elapsed times and sleeps. Polling loops in AgenticSystemsDevModeTest and BuilderTransformDevModeTest fail with NPE instead of an assertion.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Sample tests attach the Dev UI hooks once per application and shut down every RunManager they create
- [x] #2 RunShutdownTest only counts threads of the manager it shuts down
- [x] #3 UI timing checks tolerate a slow runner (wait for conditions instead of fixed windows)
- [x] #4 Polling loops fail with a clear assertion message
- [x] #5 The full build passes three times in a row in both CI configurations
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Dev UI hooks once per root: RootListeners installs a DevUiListeners group (one inherited listener, one human-step wrapper, one planner decorator); further Dev UI listeners join it and calls go to the listener owning the run; listeners of shut down managers leave.
2. Every integration test class shuts down its run managers (@AfterAll, finally).
3. RunShutdownTest checks the threads of its own manager (captured run and event threads) instead of counting.
4. UI timing checks wait for conditions; LargeSystemUiTest budgets overridable (-Dui-budget.*).
5. Polling loops assert the run was found.
6. Hardening found by the repeat runs: the event thread ends when idle, shutdown always stops the threads.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RootListenersOnceTest: three managers attached to one root give one reporting planner, each run reported by its own manager only, shut down managers leave. First repeat loop: 4/6 green; the 2 failures were filesystem errors (missing jar/classes, Input/output error writing a jar) and once HotReloadDevModeTest found 3 leftover event threads after 10 reloads (cause not identified in the log; the event thread now ends after 5 s idle and shutdown stops threads in a finally, so a manager never shut down keeps no thread). Final verification: mvn -o clean install three rounds in a row in both configurations, all green (deployment 151/155, integration 106/127, failsafe 6 incl. ProdStartupIT); ui-tests 22/22; native container build with NativeArtifactIT and ProdStartupIT green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Tests no longer interfere: Dev UI hooks are installed once per root and shared by all listeners (no stacked wrappers), every test class shuts down its run managers, the shutdown test checks its own threads, UI timing checks wait for conditions and polling loops fail with a clear message. The event thread also ends when idle. Verified by three consecutive green full builds in both configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
