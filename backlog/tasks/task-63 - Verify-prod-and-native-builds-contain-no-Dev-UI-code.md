---
id: TASK-63
title: Verify prod and native builds contain no Dev UI code
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
labels:
  - build
  - testing
milestone: m-6
dependencies:
  - TASK-59
documentation:
  - spec-breakdown.md
priority: medium
type: task
ordinal: 98000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec F6.4: works on the JVM in dev mode; native and prod builds contain no Dev UI code. Needs an automated check so a misplaced class in runtime does not slip into prod.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A test or build check asserts no runtime-dev or deployment class is in the prod artifact of the sample app
- [ ] #2 A native build of the sample app succeeds and contains no Dev UI code
<!-- AC:END -->
