---
id: TASK-20
title: 'T1.4: TopologyBuilder from declarative descriptors'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:32'
labels:
  - runtime-dev
  - topology
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.4. `TopologyBuilder` turns descriptors (and later live `AgentInstance` trees, T5.2) into the shared topology model, assigning stable node ids that runtime events will be correlated with (T2.6).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TopologyBuilder produces an AgenticSystem for each declarative root with origin DECLARATIVE and a sourceRef
- [ ] #2 Node ids are stable across builds of the same code
- [ ] #3 Unit tests cover every node kind, including nesting (loop inside sequence)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design note from TASK-18.1: the declarative descriptors (AgentDescriptor) are build-time records in the deployment module. Building the declarative topology at build time and recording AgenticSystem values avoids duplicating descriptors in runtime; the runtime-dev TopologyBuilder is then only needed for AgentInstance trees (T5.2, builder systems).
<!-- SECTION:NOTES:END -->
