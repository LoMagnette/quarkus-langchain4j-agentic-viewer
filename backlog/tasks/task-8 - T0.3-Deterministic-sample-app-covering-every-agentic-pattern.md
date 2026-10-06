---
id: TASK-8
title: 'T0.3: Deterministic sample app covering every agentic pattern'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 09:28'
labels:
  - samples
  - integration-tests
milestone: m-0
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.3. Every later task (spikes, tests, UI checks) needs a sample per pattern that runs without an API key and gives the same result every time, so CI and Playwright tests are deterministic. Subtasks split by fake model and by definition style.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All declarative and patterns sample systems run deterministically without any API key
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Builder and mixed samples moved to TASK-66 in the deferred builder milestone (decision-2).

Subtasks done: TASK-8.1 (scripted fake model), TASK-8.2 (9 declarative samples), TASK-8.4 (3 patterns samples, profile "patterns"). TASK-8.3 was moved to the deferred builder milestone as TASK-66. All samples run without any API key; declarative tests were rerun 3 times and patterns tests 3 times (clean) without failure.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Deterministic sample app in place: scripted fake ChatModel, 9 declarative samples covering every workflow kind plus supervisor, custom planner, HITL and error handler, and 3 patterns samples (GOAP, P2P, blackboard) behind a Maven profile; every sample has an end-to-end test on the fake model.
<!-- SECTION:FINAL_SUMMARY:END -->
