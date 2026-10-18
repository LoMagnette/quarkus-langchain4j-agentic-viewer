---
id: TASK-71.5
title: 'Dev UI runs: cancel and shutdown edge cases'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 19:43'
labels:
  - code-review
  - runs
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 111000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Several edge cases in the run lifecycle: RunManager.awaitHumanInput (around line 238) checks run.ended(), set later on the event thread, instead of the end claim, so a branch of a cancelled run that reaches a human step after failPendingInputs ran waits until shutdown. ErrorHandlers wraps the application error handler but an exception from the observer (e.g. RejectedExecutionException from emit after shutdown) changes the handler result. cancel() called before run.task(...) is set does not interrupt the run. Events emitted between RunFailed (cancel) and the queued end() are recorded after RunFailed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A human step reached by a cancelled run fails at once instead of waiting
- [x] #2 An exception in the Dev UI error observer never changes what the application error handler decided
- [x] #3 Cancelling a run right after startRun interrupts it
- [x] #4 No event is recorded after the terminal event of a run
- [x] #5 Tests cover the cases above
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run.endClaimed(); awaitHumanInput refuses to wait once the end is claimed (failPendingInputs may already have run).
2. ErrorHandlers: the observer wrapper reports without ever changing the handler result; re-observing unwraps the previous wrapper (a scope reused by a later run reports to that run only, no growing chain).
3. startRun creates the FutureTask and sets it on the run before executing it, so a cancel always finds the task.
4. Run records whether its terminal event was recorded; deliver drops any later event.
5. RunEndEdgeCasesTest in the runs package (package-private hooks).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RunEndEdgeCasesTest: 4 of 5 tests failed before (human step waited until timeout with a busy event thread; observer exception changed the handler result; event recorded after RunFinished; a reused scope also reported to the previous run's observer - found while fixing, same class). The cancel-right-after-start test passes either way: startRun already set the task before returning, the FutureTask only closes the window for a concurrent cancel. Both CI configurations: BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Run end edge cases fixed: a human step reached after a cancel fails at once, the error-handler observer can no longer alter the application's decision (and a reused scope reports to its current run only), the run task is known before it starts, and nothing is recorded after a run's terminal event. Verified with RunEndEdgeCasesTest and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
