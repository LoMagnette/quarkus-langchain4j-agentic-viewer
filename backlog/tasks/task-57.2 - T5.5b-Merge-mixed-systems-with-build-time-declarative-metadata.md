---
id: TASK-57.2
title: 'T5.5b: Merge mixed systems with build-time declarative metadata'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - topology
  - deferred
milestone: m-5
dependencies:
  - TASK-19
documentation:
  - spec-breakdown.md
parent_task_id: TASK-57
priority: low
type: feature
ordinal: 92000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T5.5. A builder workflow using declarative agent classes (or the reverse) is discovered at runtime, but its declarative parts have richer build-time metadata (prompts, guard method names).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Systems mixing builder and declarative parts are tagged MIXED
- [ ] #2 Declarative sub-agents inside builder systems carry their build-time details (prompts, tools, guard names)
- [ ] #3 Both mixed samples render with merged metadata
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
