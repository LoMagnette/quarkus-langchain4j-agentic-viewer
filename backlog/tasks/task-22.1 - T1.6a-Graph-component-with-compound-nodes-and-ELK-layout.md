---
id: TASK-22.1
title: 'T1.6a: Graph component with compound nodes and ELK layout'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.1). Nested patterns are drawn as compound nodes, e.g. a loop inside a sequence is a box inside a box.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A Lit component renders an AgenticSystem topology with nested patterns as compound nodes
- [ ] #2 Layout uses ELK and stays readable for every sample system
- [ ] #3 The component exposes an API to highlight a node by id (used by the live view)
<!-- AC:END -->
