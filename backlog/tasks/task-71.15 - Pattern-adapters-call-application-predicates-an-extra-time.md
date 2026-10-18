---
id: TASK-71.15
title: Pattern adapters call application predicates an extra time
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 21:10'
labels:
  - code-review
  - patterns
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 121000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
P2PAdapter calls the exit BiPredicate again on the done action and BlackboardAdapter calls the goal Predicate again on every decision to report termination. A predicate with side effects or non-deterministic results changes behaviour or reports the wrong termination reason. When the goal field cannot be read the blackboard termination is always QUIESCENT.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 With the Dev UI attached, application exit and goal predicates are called exactly as often as without it
- [x] #2 Termination reasons are still reported, or reported as unknown when they cannot be determined
- [x] #3 Pattern tests (-Ppatterns) cover a counting predicate
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. PatternAdapter.observe(planner), called by the reporting planner after init.
2. P2PAdapter wraps the planner's exit condition, BlackboardAdapter its goal (after init, which sets the default goal), in recording wrappers; termination and goalReached come from what the planner itself was answered, never from a new call.
3. Unknown (not recorded) -> no termination reason claimed.
4. PredicateCallsTest (-Ppatterns): counting exit condition and goal, Dev UI run vs application call.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PredicateCallsTest failed before (exit 2 calls vs 1, goal 4 vs 2) and passes; P2PDecisionsTest and BlackboardDecisionsTest keep their termination reasons. Blackboard canActivate (also called by the adapter) only checks scope keys and the shouldExecute flag: no application code, left as is. The exit condition field is final: set by reflection like the planner supplier (same JEP 500 caveat). Both CI configurations: BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Pattern adapters no longer call the application's predicates: the P2P exit condition and the blackboard goal are wrapped to record what they answered the planner, and the termination reason and goal status come from that record (left out when unknown). Verified with PredicateCallsTest (counting predicates, Dev UI run vs application call; failed before) and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
