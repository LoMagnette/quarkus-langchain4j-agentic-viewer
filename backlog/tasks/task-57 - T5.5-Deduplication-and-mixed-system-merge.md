---
id: TASK-57
title: 'T5.5: Deduplication and mixed-system merge'
status: Done
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 13:56'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-53
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 90000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.5 (F1.4). Split into deduplication and mixed-system merge.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A per-request builder appears once
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Split into TASK-57.1 (deduplication by call site and shape) and TASK-57.2 (mixed systems), both done; a per-request builder appears once.
<!-- SECTION:FINAL_SUMMARY:END -->
