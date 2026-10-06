---
id: TASK-10
title: 'T0.5: Spike - walk an AgentInstance tree at runtime'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 06:26'
labels:
  - spike
  - runtime-dev
  - builder
  - deferred
milestone: m-5
dependencies:
  - TASK-66
documentation:
  - spec-breakdown.md
priority: low
type: spike
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.5. Builder systems only exist at runtime, so their topology must be read from the live `AgentInstance` tree, the way `HtmlReportGenerator.generateTopology` in langchain4j-agentic does. This spike confirms which information (kind, keys, guards, planners) is reachable.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A prototype prints the full tree for one builder system and one declarative system
- [ ] #2 A written finding lists which topology-model fields are recoverable at runtime and which are not (e.g. lambda guards)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
