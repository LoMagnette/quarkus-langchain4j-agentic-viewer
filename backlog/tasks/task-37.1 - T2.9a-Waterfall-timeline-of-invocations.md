---
id: TASK-37.1
title: 'T2.9a: Waterfall timeline of invocations'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-33
documentation:
  - spec-breakdown.md
parent_task_id: TASK-37
priority: high
type: feature
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.9 (F4.2, F4.3). The timeline nests invocations by parent, the way AgentMonitor records them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every invocation is listed with start offset and duration bars, nested by parent invocation
- [ ] #2 Each loop iteration and parallel-mapper item has its own row
- [ ] #3 The timeline matches AgentMonitor output for the same sample run
<!-- AC:END -->
