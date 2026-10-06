---
id: TASK-8.4
title: 'T0.3d: GOAP, P2P and blackboard samples with langchain4j-agentic-patterns'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 09:28'
labels:
  - samples
  - patterns
milestone: m-0
dependencies:
  - TASK-8.1
  - TASK-7.2
documentation:
  - spec-breakdown.md
parent_task_id: TASK-8
priority: medium
type: task
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.3. Fixtures for Phase 4. The patterns module is optional, so these samples must be isolated (separate module or profile) to allow testing the extension both with and without it (T4.6).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Samples exist for GoalOrientedPlanner, P2PPlanner (research sample) and BlackboardPlanner
- [x] #2 The patterns samples live in a separate module or Maven profile, so the rest of the app builds without langchain4j-agentic-patterns
- [x] #3 Each sample runs deterministically with the fake model
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Maven profile "patterns" in integration-tests: adds dev.langchain4j:langchain4j-agentic-patterns and the source folders src/patterns/java and src/patterns-test/java (build-helper). Default build stays free of the module.
2. Samples (one file each, with their ChatModelScript): TripGoalPlanner (GoalOrientedPlanner; a restaurant critic not needed for the goal must be skipped), ResearchTeam (P2PPlanner with exit condition; searcher -> formulator -> critic <-> refiner, ends when the critique is SOLID), IncidentResponder (BlackboardPlanner; log and metrics analyzers ready at the same time, declaration-order conflict resolution, goal = fix).
3. PatternSampleWorkflows injects the roots (build-time validation).
4. PatternSamplesTest (@QuarkusTest, profile only): output and agent order per sample.
5. Verify mvn install (no profile) and mvn install -Ppatterns.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Samples (integration-tests/src/patterns/java, profile "patterns", each with its ChatModelScript):
- TripGoalPlanner: GoalOrientedPlanner, goal "itinerary"; RestaurantCritic is not on the path and is never called.
- ResearchTeam: P2PPlanner(10, critique starts with SOLID); searcher -> formulator -> critic <-> refiner, ends on the exit condition after 7 invocations (stable chain, no parallel races).
- IncidentResponder: BlackboardPlanner with a custom conflict resolution strategy (metrics analyzer first when ready, else declaration order); goal "fix".
PatternSampleWorkflows injects the roots. PatternSamplesTest asserts output and agent order for each.

Validation: mvn clean install (no profile): 22 integration tests, no langchain4j-agentic-patterns jar in the app and no pattern classes compiled. mvn install -Ppatterns: 25 tests pass. 3 further runs of mvn clean test -Ppatterns -Dtest=PatternSamplesTest: all pass.

Gotchas: switching the profile without clean fails (stale Quarkus app model in target/ loads patterns classes outside the Quarkus class loader: LinkageError / ClassCastException) - documented in the pom, CLAUDE.md and TASK-51. ConflictResolutionStrategy.agentWithName returns no agent when the named one is not ready, which makes the blackboard planner stop as quiescent; hence the custom strategy. No Quarkus extension exists for the patterns module (noted on TASK-46); the module also has BDI, Debate and Voting planners not covered by the spec.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added GOAP (TripGoalPlanner), P2P (ResearchTeam) and blackboard (IncidentResponder) samples on langchain4j-agentic-patterns, isolated behind the integration-tests Maven profile "patterns", scripted on the fake model and tested end to end (PatternSamplesTest). Verified the default build excludes the module and the profile build passes repeatedly with clean.
<!-- SECTION:FINAL_SUMMARY:END -->
