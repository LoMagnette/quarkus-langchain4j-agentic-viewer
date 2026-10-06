---
id: TASK-22.3
title: 'T1.6c: Loop back-edges and conditional guard labels'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies:
  - TASK-22.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.2, F2.3). Loops and conditionals carry logic that is invisible without labels.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Loops show a back-edge labelled with maxIterations
- [ ] #2 Conditional branches show the guard label: the @ActivationCondition method name for declarative, "lambda" for builder
<!-- AC:END -->
