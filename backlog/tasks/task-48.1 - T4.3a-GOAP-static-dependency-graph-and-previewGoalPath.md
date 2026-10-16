---
id: TASK-48.1
title: 'T4.3a: GOAP static dependency graph and previewGoalPath'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:12'
labels:
  - patterns
  - goap
  - json-rpc
milestone: m-4
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-48
priority: medium
type: feature
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T4.3. The GOAP planner chains agents by their input and output keys toward a goal key; the static view shows that dependency graph, and the preview predicts the path for given inputs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The GOAP planner node shows a dependency graph of agents by input and output keys with the goal key highlighted
- [x] #2 previewGoalPath(systemId, inputKeys) returns the ordered agent list using the planner graph logic
- [x] #3 Unit tests cover previewGoalPath for the GOAP sample
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. PatternAdapter gains previewPath(plannerNode, presentKeys) and a decision context (planner, action, scope, planner agent, sub-agents, first decision) returning extras plus an optional planned path of sub-agents (mapped to node ids by the listener).
2. GoapAdapter: the real langchain4j-agentic-patterns search (GoalOrientedSearchGraph, public API: reads only argument names and output keys) on stand-in agents built from the topology children for the preview, on the real sub-agents with the scope's keys at the first decision for the live path.
3. JSON-RPC previewGoalPath(systemId, inputKeys) -> {GOAP planner node id: [child node ids in order]}; empty without the module.
4. Graph: a GOAP planner node is drawn as its dependency graph: an edge per key from the sub-agent writing it to each sub-agent reading it, keys nobody inside writes as input nodes, the goal key as a highlighted goal node fed by its writer (no hub/candidates; run step numbers go on the nodes).
5. Tests: GoapAdapter preview on the GOAP sample (patterns profile, several key sets), JSON-RPC in dev mode with -Ppatterns via browser; deployment dev-mode: previewGoalPath empty without the module.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Preview and live path both run langchain4j-agentic-patterns' own GoalOrientedSearchGraph (public; reads only argument names and output keys): on stand-ins from the topology for previewGoalPath, on the real sub-agents with the scope keys at the first decision (PlannerDecision extras path/goal). Adapter API reworked: insight(Decision) -> Insight(extras, path), previewPath(node, keys). Fixed: stacked ReportingPlanners (several Dev UI listeners in tests) now match adapters on the innermost planner. Verified: GoalPathPreviewTest (patterns profile: city+budget -> HotelFinder, WeatherForecaster, ActivityPlanner, ItineraryBuilder; with forecast known -> ActivityPlanner, HotelFinder, ItineraryBuilder (the search's order); hotel+activities -> ItineraryBuilder; budget only -> no path; a run's first decision carries the previewed path and walks it); AgenticSystemsDevModeTest: previewGoalPath empty without the module; patterns integration tests 83 pass. Browser (dev mode -Ppatterns): TripGoalPlanner drawn as its key dependency graph (city/budget inputs on top, goal: itinerary at the bottom, reviewRestaurant off the goal chain), run steps 1-4 on the nodes; supervisor view unchanged.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
GOAP planner nodes are drawn as their key dependency graph with the goal key highlighted; previewGoalPath(systemId, inputKeys) returns the agents a GOAP planner would call, by the planner's own search, and the first decision of a run carries the computed path. Verified by GoalPathPreviewTest (patterns profile), the dev-mode test without the module and browser checks.
<!-- SECTION:FINAL_SUMMARY:END -->
