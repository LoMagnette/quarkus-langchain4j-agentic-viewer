---
id: TASK-19
title: 'T1.3: Root system detection and build-time data for the Dev UI'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - discovery
  - dev-ui
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.3 (F1.2). A root system is any agentic interface not referenced as a sub-agent by another; sub-agents appear only inside their parents. Without this the list would show every sub-agent as its own system.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Only root systems are listed; sub-agents appear nested in their parents
- [ ] #2 An agent used as a sub-agent of two different roots appears inside both
- [ ] #3 Root descriptors are handed to the Dev UI as build-time data and to runtime-dev via a recorder
- [ ] #4 A deployment test asserts the root set of the sample app
<!-- AC:END -->
