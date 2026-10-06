---
id: TASK-29.1
title: 'T2.2a: RunManager core: async start, run ids, lifecycle events'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-29
priority: high
type: feature
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.2. startRun must return at once with a run id; the run executes on a worker thread and its lifecycle is reported as events.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 startRun(systemId, inputs) returns a runId immediately and executes the run on a worker thread
- [ ] #2 RunStarted, RunFinished (output, duration) and RunFailed (error, duration) events are emitted
- [ ] #3 Two concurrent runs of the same system are tracked separately
<!-- AC:END -->
