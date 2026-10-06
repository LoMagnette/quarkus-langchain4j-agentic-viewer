---
id: TASK-54
title: 'T5.3: Listener and planner decorator attachment for builder systems'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - live-view
  - deferred
milestone: m-5
dependencies:
  - TASK-2
  - TASK-52
  - TASK-31
  - TASK-43
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 87000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.3. Depends on U2 (ServiceLoader observer for built systems): builder systems are constructed in user code, so the listener and planner wrapper must be injected when they are built. The T0.7 finding says whether this is possible after `.build()`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 DevUiAgentListener receives callbacks for builder systems discovered via CDI
- [ ] #2 Planners created with plannerBuilder are wrapped and emit PlannerDecision events
- [ ] #3 The live view works for a produced builder sample system
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
