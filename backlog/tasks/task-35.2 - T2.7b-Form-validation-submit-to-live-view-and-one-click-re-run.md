---
id: TASK-35.2
title: 'T2.7b: Form validation, submit to live view and one-click re-run'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - dev-ui
  - run
milestone: m-2
dependencies:
  - TASK-35.1
  - TASK-29.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-35
priority: high
type: feature
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.7 (F3.3 to F3.5).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Inputs are validated against the JSON schema before submit; missing required keys and invalid values are flagged per field
- [ ] #2 Submitting calls startRun and switches to the live view of that run
- [ ] #3 The last inputs used per system are kept for the browser session and a run can be repeated in one click
<!-- AC:END -->
