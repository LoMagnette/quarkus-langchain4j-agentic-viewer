---
id: TASK-37.3
title: 'T2.9c: Tool calls under the calling agent'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:41'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-37.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-37
priority: medium
type: feature
ordinal: 62000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.9 (F4.5).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Tool calls appear in the timeline under the agent invocation that made them
- [x] #2 Each tool call shows its arguments and result
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. buildTimeline puts ToolStarted/ToolFinished (by calling invocation id) as rows under their agent invocation, with arguments, result and duration; a tool row expands on click to show arguments and result.
2. Browser check on BankSupervisor (exchange, withdraw, credit under their agents, arguments/results).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
buildTimeline adds ToolStarted/ToolFinished as tool rows under the calling invocation (matched by invocation id and tool call id), with arguments, result and duration; a tool row expands on click to show arguments and result. Verified in headless Chromium on the sample app in dev mode (scratchpad timeline.mjs), light and dark, no page errors. BankSupervisor: exchange/withdraw/credit tools under their agents; exchange expanded: arguments {"originalCurrency": "EUR", "amount": 100, "targetCurrency": "USD"}, result 115.0. AgentMonitor comparison includes tool executions.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Tool calls appear in the timeline under the agent invocation that made them and expand to show their arguments and result. Verified on BankSupervisor in headless Chromium and against AgentMonitor's tool executions.
<!-- SECTION:FINAL_SUMMARY:END -->
