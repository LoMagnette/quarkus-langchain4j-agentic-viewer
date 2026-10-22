---
id: TASK-100
title: >-
  Series slot: owned by the series task, released after its cleanup;
  all-or-nothing acceptance
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 19:20'
updated_date: '2026-10-22 19:26'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 156000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Review of TASK-99 (2026-10-22): startSeries takes a series slot before copying the inputs and registering the series, but only end() gives it back: a failure in between (e.g. a caller map changed meanwhile) leaks it for good. end() also gives it back before pruning, while the series thread is still busy, so a new series may make the cached pool create another thread. Found as well: an Error in the series loop (only RuntimeException is caught) leaks the slot and leaves the series RUNNING; a series refused at submission stays registered (CANCELLED) although its id was never given out.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The series task owns its slot from submission and gives it back last, once the series ended and its system series were bounded; any failure before submission gives it back and leaves nothing registered
- [x] #2 A series whose thread dies with an Error ends FAILED and gives its slot back
- [x] #3 Tests: a failing input copy leaves no series and the slot free (with max-concurrent-runs=1 the next series starts)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. startSeries: all or nothing after the slot is taken (copy inputs, register, prune, submit inside try; on failure forget the series, give the slot back; RejectedExecution -> STOPPING).
2. The submitted task owns the slot: finally { end FAILED if still RUNNING (an Error); release } so the slot comes back after the series ended and was pruned; end() no longer releases.
3. SeriesTest: failing input copy leaves no series and a free slot (max-concurrent-runs=1); an Error on the series thread ends the series FAILED and frees the slot.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Review finding confirmed (permit taken before input copy / registration, released by end() before pruning); also found: an Error in the series loop leaked the slot and left the series RUNNING, a series refused at submission stayed registered. Verification: SeriesTest 10/10 (both new tests fail on the old code: Too many series at once / series stuck RUNNING); integration suite 132 green (scratch copy, committed pom); deployment 204.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The series slot now belongs to the submitted series task, which gives it back last (after the series ended and its system series were bounded), so a new series never races the thread finishing the previous one. Before submission, startSeries is all or nothing: any failure (e.g. inputs changed meanwhile, executor shut down) forgets the series and gives the slot back. A series thread dying with an Error ends the series FAILED and frees its slot. Verified by two new SeriesTest cases and the suites.
<!-- SECTION:FINAL_SUMMARY:END -->
