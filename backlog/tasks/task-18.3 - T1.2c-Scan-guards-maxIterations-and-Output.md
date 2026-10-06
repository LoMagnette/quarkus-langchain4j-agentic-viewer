---
id: TASK-18.3
title: 'T1.2c: Scan guards, maxIterations and @Output'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F2.3). Loops and conditionals are only readable in the graph if their guards are named. Declarative guards are methods, so the method name is the label.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 @ActivationCondition and loop exit-condition methods are recorded as Guard labels with their method names
- [ ] #2 maxIterations and testExitAtLoopEnd are recorded for loops
- [ ] #3 @Output methods are recorded on their workflow
- [ ] #4 Deployment tests cover a conditional and a loop fixture
<!-- AC:END -->
