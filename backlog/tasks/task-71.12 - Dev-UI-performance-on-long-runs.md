---
id: TASK-71.12
title: Dev UI performance on long runs
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:57'
labels:
  - code-review
  - dev-ui
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: enhancement
ordinal: 118000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Each event copies the whole event array in the run panel, and each run-updated makes the timeline, scope and output tabs recompute everything from all events (scope re-stringifies every value). Run.events on the server is a CopyOnWriteArrayList appended to one by one. Long runs (loops with many tool calls, thousands of events) cost O(n^2) CPU and allocation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The run panel passes a run's events to the tabs at most once per animation frame (the terminal event at once), so their work no longer grows with events x events
- [x] #2 Appending an event on the server does not copy the event list
- [x] #3 LargeSystemUiTest or a new browser test measures a run with at least 2000 events within a stated budget
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run panel: events appended in place; run-updated published at most once per animation frame (a copy of the list), at once for the terminal event and when the live view ends.
2. Run.events: a synchronized ArrayList, copied only when read (events(), subscribe).
3. LargeSystemUiTest.longRun: 'chatty' builder loop of 5000 instant iterations (10004 events), budget 20 s from Run to every timeline row, longest main-thread task < 1 s.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Measured: at 1100 iterations (2204 events) the previous code already followed in 2.9 s (Lit merges updates within a task); at 5000 iterations (10004 events) it did not reach 'succeeded' within 60 s. With per-frame publishing: 3.0 s, longest main-thread task 81 ms. AC 1 reworded: 'cost independent of the number of events' is not achievable while the timeline renders every row; batching removes the per-event full recomputation, which was the defect. ui-tests profile: 18 tests pass.

Both CI configurations: BUILD SUCCESS after the Run.events change.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Long runs no longer bog the page down: the run panel passes a run's events to the timeline, scope and output tabs at most once per animation frame (at once for the terminal event), and the server appends events without copying its list. A 10004-event run now shows completely in about 3 s (it did not finish within 60 s before); LargeSystemUiTest.longRun keeps it under a 20 s budget with no main-thread task over 1 s.
<!-- SECTION:FINAL_SUMMARY:END -->
