---
id: TASK-18.2
title: 'T1.2b: Scan supervisor, planner and human-in-the-loop annotations'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:38'
labels:
  - deployment
  - discovery
  - planner
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F1.1). Supervisors and planners have candidate sub-agents rather than a fixed order, and @HumanInTheLoop is a node kind of its own; they need dedicated descriptors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Descriptors are produced for @SupervisorAgent, @PlannerAgent and @HumanInTheLoop
- [x] #2 Planner descriptors record the planner supplier (@PlannerSupplier) class or method
- [x] #3 Deployment tests cover each annotation
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Scanner: add @SupervisorAgent (SUPERVISOR), @PlannerAgent (PLANNER) and @HumanInTheLoop (HUMAN_IN_THE_LOOP; static interface method, value used as description like @Agent).
2. AgentDescriptor gets plannerSupplier: "Class#method" of the @PlannerSupplier method declared in the same type as the @PlannerAgent method (the concrete planner class is only known when the supplier runs).
3. Fixtures: a supervisor, a planner agent with @PlannerSupplier and a sequence containing a HITL step, valid for the agentic build-time validation; extend the scanner unit tests and the QuarkusUnitTest.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scanner now also reads @SupervisorAgent (SUPERVISOR), @PlannerAgent (PLANNER) and @HumanInTheLoop (HUMAN_IN_THE_LOOP, static interface method; its value is used as description like @Agent). AgentDescriptor gained plannerSupplier = "Class#method" of the @PlannerSupplier method declared next to the @PlannerAgent method; the concrete planner class is only known when the supplier runs (pattern detection is T4.2). Fixtures: Bank supervisor over Teller, DraftPlanner with a SequentialPlanner supplier, ApprovedStory sequence with an Approval HITL step, all valid for the agentic build-time validation. Validation: mvn clean install, all tests pass (deployment 21: scanner unit tests 12, build-step QuarkusUnitTest now 19 descriptors; integration-tests 30).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Extended the annotation scanner to supervisor, planner and human-in-the-loop agents; planner descriptors reference their @PlannerSupplier method. Covered by scanner unit tests and the QuarkusUnitTest on a real fixture app.
<!-- SECTION:FINAL_SUMMARY:END -->
