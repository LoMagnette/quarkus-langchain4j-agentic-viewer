---
id: TASK-44
title: 'T3.3: Generic planner live view from decision events'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
labels:
  - dev-ui
  - live-view
  - planner
milestone: m-3
dependencies:
  - TASK-43
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.3. Planners without a dedicated pattern adapter (custom planners, or any planner when the patterns module is absent) need a generic, step-by-step view.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each planner decision is shown as a numbered step on the graph and in the timeline
- [ ] #2 Each step lists the agents selected and whether the planner signalled done
- [ ] #3 A custom planner sample run is readable step by step
<!-- AC:END -->
