---
id: TASK-47
title: 'T4.2: Identify the planner type behind each planner node'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:05'
labels:
  - runtime-dev
  - patterns
  - planner
milestone: m-4
dependencies:
  - TASK-43
  - TASK-46
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 74000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.2. Pattern-aware views need to know which planner implementation a planner node uses; this fills PlannerInfo.patternType.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Planner nodes are tagged GOAP, P2P, BLACKBOARD or CUSTOM in PlannerInfo
- [x] #2 All patterns samples and the custom planner sample are tagged correctly
- [x] #3 Without the patterns module every planner is tagged CUSTOM
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. deployment/topology PlannerTypeDetector: reads the @PlannerSupplier method's bytecode (ASM, class bytes from the build class loader) and takes the first object it constructs that is a planner: a langchain4j-agentic-patterns planner (GoalOrientedPlanner, P2PPlanner, BlackboardPlanner, by name) or a subclass of one, or another Planner implementation known to the Jandex index; it gives PlannerInfo.plannerClass and patternType (GOAP, P2P, BLACKBOARD, else CUSTOM); nothing found -> null, CUSTOM.
2. TopologyBuilder takes the detector (default: none, CUSTOM as today); the processor passes one built from the combined index and the build class loader.
3. Tests: deployment unit test on fixtures (custom planner class, SequentialPlanner, factory method, no planner), patterns profile test of the three pattern samples + DraftReviewer, default build: DraftReviewer CUSTOM with its class.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified: PlannerTypeDetectorTest (custom class, built-in SequentialPlanner, a non-planner object constructed first, factory method -> no class, unknown class/method, a stand-in pattern planner -> GOAP); PatternPlannerTypesTest (patterns profile: GOAP / P2P / BLACKBOARD with their classes); PlannerTypesTest (default: DraftReviewer CUSTOM with DraftReviewer$DraftReviewPlanner; no pattern planner can exist without the module, so every planner is CUSTOM); dev mode with -Ppatterns (scratchpad pw/planner-details.mjs): the details panel of each root shows e.g. 'GoalOrientedPlanner · goal-oriented (GOAP) pattern, from TripGoalPlanner#planner', i.e. the real build step reads the bytecode through the build class loader. Builds: patterns integration tests 79; default deployment 110, integration-tests 69.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Planner nodes are tagged with their planner class and pattern (GOAP, P2P, BLACKBOARD, CUSTOM) from the bytecode of their @PlannerSupplier, read with ASM at build time; the details panel shows them. Verified by unit tests, sample tagging tests in both profiles and dev mode with the patterns module.
<!-- SECTION:FINAL_SUMMARY:END -->
