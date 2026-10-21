---
id: TASK-92
title: >-
  Blackboard candidates: AgentActivator.canActivate changed in the langchain4j
  snapshot
status: Done
assignee:
  - '@claude'
created_date: '2026-10-21 11:08'
updated_date: '2026-10-21 11:26'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 148000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The build against the langchain4j snapshots (langchain4j-snapshots profile, 1.23.0-beta33-SNAPSHOT) fails LangChain4jInternalsTest and AgenticSystemsDevModeTest.noInternalApiWarning: upstream #6731 (activation conditions, merged 2026-10-19) changed the private `BlackboardPlanner$AgentActivator.canActivate(AgenticScope)` to `canActivate(PlanningContext)`, so the Dev UI lost the blackboard candidates. The new method also calls `PlanningContext.isActive(agent)`, which evaluates the application activation conditions: calling it from the adapter would run application predicates an extra time (the rule of TASK-71.15).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The blackboard candidates of each decision are told with the pinned langchain4j (1.20.2) and with the snapshot that has activation conditions
- [x] #2 The adapter calls no application code to find the candidates (no activation condition, goal or strategy call beyond what the planner does)
- [x] #3 LangChain4jInternals no longer lists the AgentActivator internals; LangChain4jInternalsTest passes on both versions
- [x] #4 Both CI configurations build (default and -Ppatterns)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. BlackboardAdapter.observe also wraps the planner's conflictResolutionStrategy (field kept in both versions) in a RecordedStrategy that remembers the candidates the planner hands it (its ready list), then delegates.
2. insight() takes the candidates from it (cleared once read), in declaration order; strategy() describes the wrapped strategy.
3. Drop the agentActivators / AgentActivator.agent / canActivate reflection and their LangChain4jInternals entries; the strategy entry now names the candidates too.
4. Verify: LangChain4jInternalsTest + BlackboardDecisionsTest + PredicateCallsTest on the pinned version, then -Ppatterns,langchain4j-snapshots -U; full builds of both CI configurations.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Cause confirmed: today's 1.23.0-beta33-SNAPSHOT jar has 'private boolean canActivate(PlanningContext)' (upstream #6731, isActive(agent) inside). ConflictResolutionStrategy.resolve(AgenticScope, List<AgentInstance>) and the conflictResolutionStrategy field are unchanged in 1.20.2 and the snapshot: the adapter wraps the field (RecordedStrategy, like RecordedGoal) and takes the candidates the planner hands it, i.e. its own ready list (activation conditions already applied by the planner, once). Field is private final: set by reflection, same JEP 500 caveat as TASK-71.15. Removed agentActivators, AgentActivator, its agent field and canActivate from LangChain4jInternals (3 fewer internal parts). doc-7 'Viewer today' updated.
Targeted, pinned 1.20.2 (-Ppatterns): LangChain4jInternalsTest 2/2, BlackboardDecisionsTest 2/2 (exact candidate lists per step), PredicateCallsTest 2/2. Snapshot (-U -Ppatterns,langchain4j-snapshots): LangChain4jInternalsTest 2/2, AgenticSystemsDevModeTest 12 (1 skipped, as before), BlackboardDecisionsTest 2/2, PredicateCallsTest 2/2.

Full builds: mvn clean install (deployment 201, integration-tests 107+6 IT: SUCCESS); -Ppatterns (210, 145, 6: SUCCESS); -Ppatterns,langchain4j-snapshots with today's snapshot (210, 145, 6: SUCCESS).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
langchain4j #6731 changed BlackboardPlanner$AgentActivator.canActivate(AgenticScope) to canActivate(PlanningContext), which also evaluates application activation conditions. BlackboardAdapter now wraps the planner's conflictResolutionStrategy (unchanged API in both versions) and tells the candidates the planner hands it; the AgentActivator internals are gone from LangChain4jInternals. Verified with BlackboardDecisionsTest, PredicateCallsTest and LangChain4jInternalsTest on 1.20.2 and on the 2026-10-21 snapshot, and full builds of default, -Ppatterns and -Ppatterns,langchain4j-snapshots.
<!-- SECTION:FINAL_SUMMARY:END -->
