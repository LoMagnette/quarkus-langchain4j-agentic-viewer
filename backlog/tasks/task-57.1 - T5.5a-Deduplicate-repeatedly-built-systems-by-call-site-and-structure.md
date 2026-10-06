---
id: TASK-57.1
title: 'T5.5a: Deduplicate repeatedly built systems by call site and structure'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-57
priority: low
type: feature
ordinal: 91000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T5.5 (F1.4). Systems built more than once (e.g. per request) would otherwise flood the list.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Systems built several times from the same call site with the same structure map to one registry entry
- [ ] #2 Runs of any instance are attributed to that entry
- [ ] #3 The per-request builder sample appears once after several requests
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
