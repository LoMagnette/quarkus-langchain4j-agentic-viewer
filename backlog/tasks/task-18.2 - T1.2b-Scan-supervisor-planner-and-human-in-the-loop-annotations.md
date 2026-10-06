---
id: TASK-18.2
title: 'T1.2b: Scan supervisor, planner and human-in-the-loop annotations'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - discovery
  - planner
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F1.1). Supervisors and planners have candidate sub-agents rather than a fixed order, and @HumanInTheLoop is a node kind of its own; they need dedicated descriptors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Descriptors are produced for @SupervisorAgent, @PlannerAgent and @HumanInTheLoop
- [ ] #2 Planner descriptors record the planner supplier (@PlannerSupplier) class or method
- [ ] #3 Deployment tests cover each annotation
<!-- AC:END -->
