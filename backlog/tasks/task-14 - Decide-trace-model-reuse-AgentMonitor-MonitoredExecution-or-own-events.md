---
id: TASK-14
title: 'Decide trace model: reuse AgentMonitor/MonitoredExecution or own events'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - decision
  - runtime-dev
milestone: m-0
dependencies:
  - TASK-9
documentation:
  - spec-breakdown.md
priority: medium
type: spike
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Open question from spec-breakdown.md "Testing, risks and open questions". langchain4j-agentic already records invocations in `AgentMonitor` / `MonitoredExecution`. Reusing it would align the waterfall timeline (T2.9) with upstream, but the spec also defines its own execution event model (planner decisions, scope state, HITL). Decide before the event model records are written.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A Backlog decision records whether the Dev UI reuses AgentMonitor/MonitoredExecution, keeps its own events, or combines them
- [ ] #2 The decision lists which spec events cannot be derived from AgentMonitor, if any
<!-- AC:END -->
