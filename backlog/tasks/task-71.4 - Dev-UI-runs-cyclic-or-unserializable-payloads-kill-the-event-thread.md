---
id: TASK-71.4
title: 'Dev UI runs: cyclic or unserializable payloads kill the event thread'
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
priority: medium
type: bug
ordinal: 110000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PayloadTruncator.toTree catches only IllegalArgumentException from Jackson valueToTree. A Map or List containing itself throws StackOverflowError, which RunManager.deliver (catch RuntimeException) does not catch either: the event is lost and the event thread dies. If it is the run output, RunFinished is lost while the run is marked SUCCEEDED and the Dev UI stays on running forever. Very large values are also fully serialized before any truncation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A run whose output or agent input contains a self-referencing Map or List finishes with a RunFinished event carrying a placeholder for the value
- [x] #2 An Error or exception thrown while delivering one event does not stop the delivery of later events
- [x] #3 Tests cover a self-referencing map, a self-referencing list and an exception thrown by a value toString or getter
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. PayloadTruncator never throws: toTree catches RuntimeException and StackOverflowError (Jackson recurses without end into a self-containing map/list) and returns a marker; truncateValues survives a failing map iteration.
2. RunManager.deliver: a failure bounding a payload still records RunFinished (without output); a listener failing with an Error (except VM errors other than StackOverflowError) does not stop the other listeners.
3. Tests: PayloadTruncatorTest (self-referencing map and list, failing getter/toString), RunManagerTest (cyclic run output ends with RunFinished, listener throwing AssertionError).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
New tests failed before (StackOverflowError; run SUCCEEDED but last event RunStarted; second listener received nothing) and pass now. Agent inputs go through truncateValues, covered by the unit test. Both CI configurations: BUILD SUCCESS (deployment 133/137, integration 105/124). Not done: very large values are still serialized fully before truncation (described in the task, not an acceptance criterion; part of TASK-71.12 performance if wanted).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Payload bounding can no longer lose an event: cyclic or unserializable values (self-containing maps/lists, failing getters) become a marker, the event thread records RunFinished even if bounding failed, and a listener throwing an Error no longer stops the others. Verified with new PayloadTruncatorTest/RunManagerTest cases (fail before) and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
