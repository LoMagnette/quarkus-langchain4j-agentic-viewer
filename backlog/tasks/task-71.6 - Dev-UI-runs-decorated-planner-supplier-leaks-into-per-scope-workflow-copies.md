---
id: TASK-71.6
title: 'Dev UI runs: decorated planner supplier leaks into per-scope workflow copies'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:01'
labels:
  - code-review
  - runs
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 112000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PlannerDecisions replaces each workflow plannerSupplier with a decorating one. langchain4j withAgenticScope builds the per-scope copy defaultPlannerInstance from that supplier, so a supervisor sub-workflow copy reports plannerType() as the Dev UI ReportingPlanner and accessChatMemory casts it to ChatMemoryAccessProvider, throwing ClassCastException. The decorator must stay invisible to the application.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A supervisor sub-workflow run with the Dev UI attached reports its original planner type
- [x] #2 Chat memory access of a supervisor works with the Dev UI attached
- [x] #3 A test covers both
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. The decorated planner supplier returns the original planner when called by the PlannerBasedInvocationHandler constructor (StackWalker, a few frames): the default planner instance of the handler and of its per-scope copies stays the original, invocations still get the reporting planner.
2. List the constructor in LangChain4jInternals.
3. PlannerDecisionEventsTest.perScopeCopiesKeepTheOriginalPlanner (fails before).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
perScopeCopiesKeepTheOriginalPlanner failed before (copy plannerType ReportingPlanner) and passes; the copy's default planner instance is now the SupervisorPlanner itself, which is what accessChatMemory casts to ChatMemoryAccessProvider (AC 2 verified through that instance, the copy proxy exposes no ChatMemoryAccess method to call directly). Existing PlannerDecisionEventsTest decisions unchanged; LangChain4jInternalsTest passes with the constructor listed. Both CI configurations: BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The Dev UI planner wrapper is no longer visible to langchain4j: the planner a workflow handler (and each per-scope copy) keeps as its default instance is the original one, while invocations still get the reporting planner. Verified with PlannerDecisionEventsTest.perScopeCopiesKeepTheOriginalPlanner (fails before) and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
