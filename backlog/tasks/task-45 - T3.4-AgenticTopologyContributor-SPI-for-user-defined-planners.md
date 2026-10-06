---
id: TASK-45
title: 'T3.4: AgenticTopologyContributor SPI for user-defined planners'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
labels:
  - runtime
  - spi
  - planner
milestone: m-3
dependencies:
  - TASK-44
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.4 (F5 custom planner row). A custom planner may have structure the generic view cannot infer. An optional SPI in `runtime` lets users describe its topology; it must stay a pure interface so prod builds carry no behaviour.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An AgenticTopologyContributor interface in runtime lets a planner contribute nodes and edges to its planner node
- [ ] #2 Contributors are discovered and applied in dev mode only
- [ ] #3 The sample custom planner draws its own topology through the SPI
- [ ] #4 The SPI is documented with an example
<!-- AC:END -->
