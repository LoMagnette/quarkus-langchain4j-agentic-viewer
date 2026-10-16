---
id: TASK-39.2
title: 'T2.11b: Paused view with human input box'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 15:33'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-39.1
  - TASK-36.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-39
priority: medium
type: feature
ordinal: 66000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.11 (F4.7).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 When AwaitingHumanInput arrives, the view shows a paused state with the prompt and an input box
- [x] #2 Submitting calls provideHumanInput and the live view resumes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. LiveRun tracks AwaitingHumanInput events per invocation (cleared by the step's AgentFinished/AgentFailed or the run end); graph state 'waiting' (dashed border) and 'waiting for input' node info; timeline row detail.
2. Run panel: paused state 'Waiting for your input' with the prompt, the step and key, its inputs and an answer box (JSON hint for non-String steps); Send calls provideHumanInput, shows errors (wrong type, nothing waiting) and the live view resumes with the next events.
3. Verify in headless Chromium (light, dark, reopened from the history after a reload).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified with scratchpad pw/hitl.mjs against dev mode: TripPlanner pauses (status waiting, prompt 'Asks the traveler to approve the destination', inputs 'destination: Lisbon', node TravelerApproval waiting with 'waiting for input', timeline row 'askTraveler waiting for input'), Send resumes and the run succeeds ('Day 1, 2 and 3 in Lisbon'; 'No' gives the refusal output in dark theme); a page reload then opening the running run from the history shows the paused state again and can be answered. No page errors.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run panel shows a paused state when a step waits for human input (prompt, step, inputs, answer box; JSON hint for non-String answers), the graph node is dashed and labelled 'waiting for input', the timeline row too; Send calls provideHumanInput and the live view resumes. Verified in headless Chromium in light and dark themes and after a reload through the history.
<!-- SECTION:FINAL_SUMMARY:END -->
