---
id: TASK-39
title: 'T2.11: Human-in-the-loop waiting state and provideHumanInput'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 15:33'
labels:
  - runtime-dev
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-32
  - TASK-4
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 64000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.11 (F4.7). Depends on U4 (pending-response abstraction) so the Dev UI can answer a @HumanInTheLoop step. Split into backend and UI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The HITL sample run completes from the UI
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The HITL sample (TripPlanner) completes from the Dev UI: the step waits, the run panel answers it, the run resumes (TASK-39.1 backend, TASK-39.2 UI). Upstream U4 is not needed: steps are intercepted in Dev UI runs (user decision).
<!-- SECTION:FINAL_SUMMARY:END -->
