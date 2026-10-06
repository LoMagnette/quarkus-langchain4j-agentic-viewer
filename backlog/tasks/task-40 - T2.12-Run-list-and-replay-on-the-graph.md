---
id: TASK-40
title: 'T2.12: Run list and replay on the graph'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-37
  - TASK-29.2
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.12 (F4.9). Runs are not persisted across restarts or hot reloads (out of scope), only kept in the bounded in-memory history.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A run list per system shows past runs with status, start time and duration
- [ ] #2 Opening a past run replays its events on the graph, timeline and scope panel
- [ ] #3 A past run replays after a page refresh
<!-- AC:END -->
