---
id: TASK-29.2
title: 'T2.2b: Bounded run history, listRuns/getRun and cancelRun'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies:
  - TASK-29.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-29
priority: medium
type: feature
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.2 (F4.9, F6.3). Past runs are reopened and replayed from the history; history must be bounded. Cancel is best effort since agents may be inside a blocking model call.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 History keeps the last N runs per system (default 20) with their full event trace and drops the oldest beyond that
- [ ] #2 listRuns(systemId) returns run summaries and getRun(runId) the full trace
- [ ] #3 cancelRun(runId) attempts cancellation, returns whether it was applied, and the run ends with a cancelled status
<!-- AC:END -->
