---
id: TASK-44
title: 'T3.3: Generic planner live view from decision events'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 19:20'
labels:
  - dev-ui
  - live-view
  - planner
milestone: m-3
dependencies:
  - TASK-43
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.3. Planners without a dedicated pattern adapter (custom planners, or any planner when the patterns module is absent) need a generic, step-by-step view.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each planner decision is shown as a numbered step on the graph and in the timeline
- [x] #2 Each step lists the agents selected and whether the planner signalled done
- [x] #3 A custom planner sample run is readable step by step
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. LiveRun: PlannerDecision events numbered per planner node; each chosen agent's candidate edge shows its step numbers (agents chosen together share one); the planner node shows 'step N', then 'done after N steps'. The T3.1 numbering from AgentStarted stays as fallback for a planner without decisions.
2. Timeline: one 'Step N' row per decision under the planner's invocation, with the chosen agents or done / done with result.
3. Verify in headless Chromium on DraftReviewer (custom planner) and BankSupervisor, live and replayed, light and dark.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified with scratchpad pw/planner.mjs against dev mode (ollama profile, qwen2.5:14b): DraftReviewer (custom @PlannerSupplier planner) live: node 'step 1'..'step 6', edges Drafter=1, 3, 5 / Reviewer=2, 4, 6, then 'done after 6 steps · 64 s'; timeline 'Step 1 → writeDraft' ... 'Step 6 → review', 'Done'; same after replay from the history; light and dark. BankSupervisor: steps 1-3 exchange, withdraw, credit, 'Done with result'. supervisor.mjs and hitl.mjs re-run unchanged, no page errors. Ending the workflow is shown as an unnumbered Done row so the node count and the timeline agree; step rows use the agents' names from their AgentStarted events.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Planner and supervisor runs are readable step by step: each decision is a numbered step on the graph (step numbers on the chosen agents' edges, step count and done on the planner node) and a 'Step N → agents' row in the timeline, with an unnumbered Done / Done with result row when the planner ends. Verified in headless Chromium on DraftReviewer (custom planner) and BankSupervisor, live and replayed, in both themes.
<!-- SECTION:FINAL_SUMMARY:END -->
