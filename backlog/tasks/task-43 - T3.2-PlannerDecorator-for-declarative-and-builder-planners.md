---
id: TASK-43
title: 'T3.2: PlannerDecorator for declarative planners'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - planner
milestone: m-3
dependencies:
  - TASK-12
  - TASK-31
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.2. Builds on the T0.7 spike. Until U3 lands, the only way to see planner decisions is to wrap any `Supplier<Planner>` and report each `Action` from `firstAction` / `nextAction`. If U3 is merged first, this task can use the listener callbacks instead.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 PlannerDecorator wraps the planner supplier of every declarative (@PlannerSupplier) planner node
- [ ] #2 A PlannerDecision event (nodeId, agents selected, done flag) is emitted for each Action
- [ ] #3 Decision events are emitted for every planner in the sample app
- [ ] #4 The decorator does not change the planner behaviour (sample outputs unchanged)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scope reduced to declarative planners (decision-2); builder plannerBuilder wrapping is covered by T5.3.
<!-- SECTION:NOTES:END -->
