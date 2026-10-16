---
id: TASK-22.4
title: 'T1.6d: Supervisor and planner candidates as dashed edges'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:08'
labels:
  - dev-ui
  - graph
  - planner
milestone: m-1
dependencies:
  - TASK-22.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.4). Supervisors and planners choose sub-agents at runtime, so drawing them in a fixed order would be misleading.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Supervisor and generic planner nodes link to their sub-agents with dashed candidate edges, with no implied order
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SUPERVISOR and PLANNER: a helper hub node inside the box (labelled 'decides' for a supervisor, the planner pattern for a planner) with dashed, arrowless-order candidate edges to every sub-agent, no edges between sub-agents, so no order is implied; sub-agents laid out side by side in declaration order.
2. Supervisor/planner hub drawn in the kind color; edges dashed with a small arrow (a candidate can be called), no labels.
3. Verify BankSupervisor and DraftReviewer in headless Chromium plus all previous checks.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERVISOR and PLANNER: helper hub node (compoundId::hub, filled circle in the kind color) with dashed CANDIDATE edges to every sub-agent and no edges between sub-agents, so no order is implied; sub-agents side by side in declaration order. Verified in headless Chromium (light and dark): BankSupervisor 3 candidate edges (exchange, withdraw, credit), DraftReviewer 2, no sequence edges in either; all previous layout checks pass on the 9 samples and the synthetic system.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Supervisor and planner sub-agents hang off a hub with dashed candidate edges and no edges between them, so no order is implied. Verified on BankSupervisor and DraftReviewer in headless Chromium, light and dark.
<!-- SECTION:FINAL_SUMMARY:END -->
