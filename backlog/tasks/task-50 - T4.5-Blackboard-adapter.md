---
id: TASK-50
title: 'T4.5: Blackboard adapter'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:35'
labels:
  - patterns
  - blackboard
milestone: m-4
dependencies:
  - TASK-47
  - TASK-3
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 81000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.5 (F5 blackboard row). Depends on U3 because the ready candidates are not visible through the planner decorator alone. Split into static and live view.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each step shows the ready candidates and the one picked
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Without U3 (user: keep going), like P2P: the adapter reads the planner's private state by reflection, isolated and guarded.
1. BlackboardAdapter.insight per decision: candidates (activators that can fire after the decision + the picked one, declaration order), strategy (declaration order / custom (class) / built-in), invocations / maxInvocations, goalReached (goal predicate on the scope); on done termination GOAL_REACHED or QUIESCENT.
2. Graph: a blackboard planner node draws a central blackboard node (the scope) with each agent reading (labelled input keys) and writing (output key) on it.
3. Live: candidates highlighted 'ready' per step, the picked one runs; planner node 'invocations n/max · goal not reached' then 'stopped: goal reached'; timeline step rows '→ picked (of a, b)', Done row with the reason; strategy in the node info.
4. Tests: patterns profile decisions of IncidentResponder (candidates per step, strategy custom (IncidentResponder), goal reached) and a quiescent test system; browser check.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done without U3 (user decision: keep going): BlackboardAdapter reads BlackboardPlanner's private activators (canActivate, agent), strategy, goal predicate and maximum by reflection, guarded; candidates = activators still able to fire after the decision + the picked one (picking marks it fired), declaration order. Verified: BlackboardDecisionsTest (patterns profile): IncidentResponder steps 'MetricsAnalyzer of [LogAnalyzer, MetricsAnalyzer]', 'LogAnalyzer of [LogAnalyzer]', 'Diagnoser of [Diagnoser]', 'FixProposer of [FixProposer]', strategy custom (IncidentResponder), goal not reached until done, GOAL_REACHED after 4 invocations; test-only QuietBoard stops QUIESCENT after 1. Browser (scratchpad pw/board.mjs, dev mode -Ppatterns, light and dark): board view, LogAnalyzer lit as the other candidate while MetricsAnalyzer runs, planner 'stopped: goal reached', timeline step rows wrap to show candidates and strategy in full; P2P and GOAP checks unchanged. Builds: patterns integration 88; default deployment 110, integration-tests 69.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Blackboard runs show, per step, the candidates that could fire, the one the conflict resolution strategy picked and the strategy, the goal status and the invocations against the maximum, then why the planner stopped (goal reached or quiescent); the static view draws the agents around a central blackboard. Done without upstream U3 by reading the planner's state through reflection. Verified by BlackboardDecisionsTest and in headless Chromium.
<!-- SECTION:FINAL_SUMMARY:END -->
