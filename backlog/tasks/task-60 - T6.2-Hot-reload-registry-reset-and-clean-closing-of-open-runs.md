---
id: TASK-60
title: 'T6.2: Hot reload: registry reset and clean closing of open runs'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
labels:
  - runtime-dev
  - hot-reload
milestone: m-6
dependencies:
  - TASK-29
  - TASK-32
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 95000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.2 (F1.5). A hot reload must clear and rebuild the registry; open runs and streams must not leak threads or reference the old classloader.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A hot reload clears and rebuilds AgenticSystemRegistry
- [ ] #2 Open runs are ended with a clear status and their streams completed
- [ ] #3 No leaked threads after 10 reloads
- [ ] #4 A dev-mode test changes an agent interface and checks the registry updates
<!-- AC:END -->
