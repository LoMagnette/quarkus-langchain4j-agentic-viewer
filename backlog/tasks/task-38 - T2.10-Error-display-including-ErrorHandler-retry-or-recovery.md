---
id: TASK-38
title: 'T2.10: Error display including @ErrorHandler retry or recovery'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.10 (F4.8). An error may be fatal or handled by an @ErrorHandler that retries or recovers; the user must see which happened.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A failed run shows the exception and the failing agent, highlighted on the graph
- [ ] #2 When an @ErrorHandler retried or recovered, the outcome is shown on the agent and in the timeline
- [ ] #3 The failing sample shows agent, exception and outcome
<!-- AC:END -->
