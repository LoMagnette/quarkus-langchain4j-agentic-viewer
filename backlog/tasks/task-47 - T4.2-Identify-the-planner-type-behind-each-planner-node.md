---
id: TASK-47
title: 'T4.2: Identify the planner type behind each planner node'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
labels:
  - runtime-dev
  - patterns
  - planner
milestone: m-4
dependencies:
  - TASK-43
  - TASK-46
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 74000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.2. Pattern-aware views need to know which planner implementation a planner node uses; this fills PlannerInfo.patternType.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Planner nodes are tagged GOAP, P2P, BLACKBOARD or CUSTOM in PlannerInfo
- [ ] #2 All patterns samples and the custom planner sample are tagged correctly
- [ ] #3 Without the patterns module every planner is tagged CUSTOM
<!-- AC:END -->
