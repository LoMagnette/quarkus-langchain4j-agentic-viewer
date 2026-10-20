---
id: TASK-90
title: 'Runs: accepting a run is all-or-nothing, also against a shutdown'
status: Done
assignee: []
created_date: '2026-10-20 16:33'
updated_date: '2026-10-20 16:51'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 146000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (medium), follow-up of TASK-89: `RunManager.startRun` takes a run-thread permit, then stores the run, emits RunStarted and submits it outside any cleanup. A shutdown meanwhile makes `emit` reject (the permit leaks) or `executor.execute` reject (the permit is given back but the run stays RUNNING in the history forever).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A run asked after shutdown is refused at once with a clear error, nothing is created
- [x] #2 Any failure after the permit is taken gives it back and leaves no run in the runs or the history (a shutdown meanwhile is reported as such)
- [x] #3 Tests cover the refusal after shutdown, a shutdown during the start and a failing start, and fail on the previous code
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Refuse when shut down before taking the permit. 2. Wrap everything after the permit in try/catch: release it, forget the run (claim its end, end it, remove it from runs and history), rethrow (as the stopping error when shut down meanwhile). 3. RunManagerTest: after shutdown; a map whose copy shuts the manager down; a map whose copy fails with a limit of 1.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
startRun refuses when shut down (before the permit); everything after the permit is in try/catch: release, forget(run) (claimEnd, Run.end CANCELLED so queued events are not delivered, removed from runs and history), rethrow (IllegalStateException 'stopping or reloading' when shut down meanwhile). Tests noRunStartsOnceShutDown, aShutdownDuringTheStartLeavesNoRunBehind (a map whose copy shuts the manager down), aFailedStartGivesItsThreadBack (limit 1): all three fail on the previous RunManager (RejectedExecutionException; leaked permit -> 'Too many runs at once'). Also fixed a race in TASK-89's aCancelledRunKeepsItsThreadUntilItReturns (seen in a full build): it cancelled before the thread took the run, which correctly frees the slot at once; it now waits until the run works (5/5 runs). Validation: mvn -o clean install 201/107/6 (1 skipped); mvn -o clean install -Ppatterns 210 (1 skipped)/145/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Accepting a run is all-or-nothing: refused at once after shutdown, and any failure after the thread permit is taken gives it back and leaves no run behind. Verified with three new RunManagerTest cases that fail on the previous code, and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
