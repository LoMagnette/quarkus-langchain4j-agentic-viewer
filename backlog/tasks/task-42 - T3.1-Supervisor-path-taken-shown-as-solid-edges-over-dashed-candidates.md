---
id: TASK-42
title: 'T3.1: Supervisor path taken shown as solid edges over dashed candidates'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 18:54'
labels:
  - dev-ui
  - live-view
  - planner
milestone: m-3
dependencies:
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.1 (F4.6). A supervisor picks sub-agents at runtime; the static graph shows them as dashed candidates (T1.6d). The live view must show which ones were actually called and in what order.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 During and after a supervisor run, the sub-agents actually invoked are linked by solid, numbered edges over the dashed candidates
- [x] #2 The bank sample shows exchange, withdraw, credit in that order
- [x] #3 Replay of a past supervisor run shows the same path
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. LiveRun: on AgentStarted under a SUPERVISOR or PLANNER node, record the call order per parent and mark the child (graph.setPath) with its call numbers.
2. Graph: setPath turns the hub->child candidate edge solid (supervisor colour, bold number label); takenPath() for checks; the run overlay (states, infos, paths) is kept and re-applied after redraws (view or theme switch), cleared by resetRun and on a new system.
3. Verify in headless Chromium on BankSupervisor: during, after, after a view toggle, replay after reload; light and dark.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified with scratchpad pw/supervisor.mjs on the sample app (ollama profile, qwen2.5:14b): candidates dashed before the run; during the run the path grows ExchangeAgent=1, then WithdrawAgent=2, then CreditAgent=3; same after the run, after a Data flow/Structure toggle (node states kept too) and when replaying the run from the history after a reload; light and dark, no page errors. hitl.mjs re-run unchanged. The run overlay fix (redraw kept highlights) was needed for 'during and after' to hold across view switches.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Supervisor (and planner) runs show the path taken: each called sub-agent's dashed candidate edge becomes solid with its call numbers, live and on replay; the graph now re-applies the run overlay after redraws. Verified in headless Chromium on BankSupervisor (exchange 1, withdraw 2, credit 3) in both themes, across a view toggle and on replay.
<!-- SECTION:FINAL_SUMMARY:END -->
