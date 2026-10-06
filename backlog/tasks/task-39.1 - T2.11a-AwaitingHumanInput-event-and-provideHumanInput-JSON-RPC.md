---
id: TASK-39.1
title: 'T2.11a: AwaitingHumanInput event and provideHumanInput JSON-RPC'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - json-rpc
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-39
priority: medium
type: feature
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.11.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An AwaitingHumanInput event with runId, prompt and expected key is emitted when a HITL step waits
- [ ] #2 provideHumanInput(runId, value) completes the pending step and returns true, or false if nothing is pending
- [ ] #3 A JSON-RPC test completes the HITL sample
<!-- AC:END -->
