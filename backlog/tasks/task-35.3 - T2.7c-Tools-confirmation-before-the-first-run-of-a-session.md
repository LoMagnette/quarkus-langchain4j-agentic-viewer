---
id: TASK-35.3
title: 'T2.7c: Tools confirmation before the first run of a session'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - dev-ui
  - run
milestone: m-2
dependencies:
  - TASK-35.2
documentation:
  - spec-breakdown.md
parent_task_id: TASK-35
priority: high
type: feature
ordinal: 55000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.7 (F3.6). Runs call real tools (and possibly real models), with real side effects and cost. The user must be told before the first run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Before the first run of a session, a dialog lists every tool reachable from the system
- [ ] #2 The run starts only after confirmation; cancelling starts nothing
- [ ] #3 The dialog is not shown again for that system in the same session
<!-- AC:END -->
