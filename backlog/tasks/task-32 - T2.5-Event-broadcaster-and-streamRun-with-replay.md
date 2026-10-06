---
id: TASK-32
title: 'T2.5: Event broadcaster and streamRun with replay'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - json-rpc
  - live-view
milestone: m-2
dependencies:
  - TASK-31
  - TASK-29.1
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.5. The UI subscribes after startRun returns, by which time events may already have been emitted, so the stream must replay them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 streamRun(runId) returns a Multi<ExecutionEvent> that replays already emitted events, then streams live ones
- [ ] #2 A subscriber joining after the run finished still sees the full run
- [ ] #3 The stream completes when the run ends
- [ ] #4 The broadcaster is non-blocking
<!-- AC:END -->
