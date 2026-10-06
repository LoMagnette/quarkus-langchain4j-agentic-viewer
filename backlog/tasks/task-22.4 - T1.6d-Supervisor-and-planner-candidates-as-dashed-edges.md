---
id: TASK-22.4
title: 'T1.6d: Supervisor and planner candidates as dashed edges'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - dev-ui
  - graph
  - planner
milestone: m-1
dependencies:
  - TASK-22.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.4). Supervisors and planners choose sub-agents at runtime, so drawing them in a fixed order would be misleading.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Supervisor and generic planner nodes link to their sub-agents with dashed candidate edges, with no implied order
<!-- AC:END -->
