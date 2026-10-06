---
id: TASK-48.1
title: 'T4.3a: GOAP static dependency graph and previewGoalPath'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
labels:
  - patterns
  - goap
  - json-rpc
milestone: m-4
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-48
priority: medium
type: feature
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T4.3. The GOAP planner chains agents by their input and output keys toward a goal key; the static view shows that dependency graph, and the preview predicts the path for given inputs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The GOAP planner node shows a dependency graph of agents by input and output keys with the goal key highlighted
- [ ] #2 previewGoalPath(systemId, inputKeys) returns the ordered agent list using the planner graph logic
- [ ] #3 Unit tests cover previewGoalPath for the GOAP sample
<!-- AC:END -->
