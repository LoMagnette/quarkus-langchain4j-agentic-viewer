---
id: TASK-27
title: Execution event model records
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
updated_date: '2026-10-06 11:12'
labels:
  - runtime-dev
  - model
  - live-view
milestone: m-2
dependencies:
  - TASK-17
  - TASK-14
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not a separate spec task; prerequisite of T2.2, T2.4 and T2.5. The spec "Execution event model" table defines the events streamed to the UI. They must exist before the RunManager, listener and broadcaster can emit them, and their shape depends on the AgentMonitor reuse decision.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every event in the spec Execution event model table exists with its key fields (RunStarted ... AwaitingHumanInput)
- [ ] #2 Every event carries runId and a timestamp and serializes to JSON
- [ ] #3 The model follows the recorded trace-model decision
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-14 (decision-4): own event records as in the spec; reuse AgentMonitor semantics for agent ids, parent correlation (agent().parent().agentId()), loop iteration index and token usage (AgentResponse.chatResponse().metadata().tokenUsage()).
<!-- SECTION:NOTES:END -->
