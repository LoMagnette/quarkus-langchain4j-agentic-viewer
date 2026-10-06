---
id: TASK-12
title: 'T0.7: Spike - identify and wrap a Supplier<Planner>'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 06:26'
labels:
  - spike
  - runtime-dev
  - planner
milestone: m-0
dependencies:
  - TASK-8
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.7. Also answers the open question "Can a listener or planner wrapper be added after .build(), or only before?".

Planner decisions are only observable by decorating the planner supplier (until U3). In the declarative-first scope (decision-2) planners come from declarative `@PlannerSupplier` methods; wrapping `plannerBuilder` planners is deferred to T5.3.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A decorated planner emits each Action returned by firstAction/nextAction in the custom-planner sample
- [ ] #2 The finding states whether wrapping is possible after .build() or only before
- [ ] #3 The finding covers declarative @PlannerSupplier planners
<!-- AC:END -->
