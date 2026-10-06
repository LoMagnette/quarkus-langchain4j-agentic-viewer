---
id: TASK-41
title: JSON-RPC event sequence tests per pattern kind
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - testing
  - json-rpc
milestone: m-2
dependencies:
  - TASK-30
  - TASK-32
  - TASK-33
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the spec testing strategy: "start a run, subscribe, assert the event sequence for sequence, loop, parallel, conditional, supervisor and each planner". Guards the listener, correlation and broadcaster against regressions and upstream API changes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Tests start a run over JSON-RPC, subscribe and assert the exact event sequence for sequence, loop, parallel, parallel mapper, conditional and supervisor samples
- [ ] #2 Tests run on the fake ChatModel in CI
<!-- AC:END -->
