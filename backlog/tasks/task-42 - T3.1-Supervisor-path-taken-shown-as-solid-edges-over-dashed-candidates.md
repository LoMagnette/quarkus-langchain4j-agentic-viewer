---
id: TASK-42
title: 'T3.1: Supervisor path taken shown as solid edges over dashed candidates'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
labels:
  - dev-ui
  - live-view
  - planner
milestone: m-3
dependencies:
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.1 (F4.6). A supervisor picks sub-agents at runtime; the static graph shows them as dashed candidates (T1.6d). The live view must show which ones were actually called and in what order.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 During and after a supervisor run, the sub-agents actually invoked are linked by solid, numbered edges over the dashed candidates
- [ ] #2 The bank sample shows exchange, withdraw, credit in that order
- [ ] #3 Replay of a past supervisor run shows the same path
<!-- AC:END -->
