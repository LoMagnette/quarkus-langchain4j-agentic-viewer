---
id: TASK-24
title: 'T1.8: Data-flow view toggle based on scope keys'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies:
  - TASK-22
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.8 (F2.6). Agents communicate through AgenticScope keys rather than direct calls; a data-flow view shows which agent produces what another consumes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A toggle switches between the structure view and a data-flow view
- [ ] #2 In the data-flow view, keys are shown as links from the writing agent to the reading agents
- [ ] #3 Keys read but never written (system inputs) are visually distinguished
<!-- AC:END -->
