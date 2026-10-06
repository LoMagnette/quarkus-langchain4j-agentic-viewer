---
id: TASK-56
title: 'T5.4: Optional dev-mode bytecode transform discovery'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - deployment
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-11
  - TASK-53
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 89000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.4 (F1.3.2, the `transform` strategy). Opt-in fallback for builder systems that are not beans, until U2 is available. Becomes unnecessary once U2 lands; check U2 status before starting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 With builder-discovery=transform, a dev-mode transform registers every .build() result of an AgenticServices builder
- [ ] #2 Non-bean builder samples are listed when enabled
- [ ] #3 The transform is never applied in prod or when another strategy is selected
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
