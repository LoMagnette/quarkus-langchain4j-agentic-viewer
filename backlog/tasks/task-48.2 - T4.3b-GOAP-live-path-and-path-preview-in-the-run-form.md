---
id: TASK-48.2
title: 'T4.3b: GOAP live path and path preview in the run form'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:17'
labels:
  - patterns
  - goap
  - dev-ui
milestone: m-4
dependencies:
  - TASK-48.1
  - TASK-44
documentation:
  - spec-breakdown.md
parent_task_id: TASK-48
priority: medium
type: feature
ordinal: 77000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T4.3.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The run form shows the path preview for the inputs entered
- [x] #2 During a run the computed path is highlighted, then walked step by step
- [x] #3 For the GOAP sample the preview matches the path the run takes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run form: for a system with GOAP planners, the run panel asks previewGoalPath with the keys of the filled inputs (on load and as inputs change, debounced) and shows 'Planned path: a → b → c' (or 'no path to <goal> from these inputs').
2. Live: the first PlannerDecision carrying extras.path marks the path's nodes 'planned' on the graph (dashed primary outline, numbered 'plan n'); as the steps run they turn running/done; nodes off the path stay plain.
3. Verify in the browser (dev mode -Ppatterns): preview for city+budget and city only, run highlights then walks the path, preview equals the run's path, replay.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified with scratchpad pw/goap.mjs (dev mode -Ppatterns, light and dark): the run form shows 'no path to itinerary from these inputs' for an empty form and city only, 'findHotel → forecast → suggestActivities → buildItinerary' once budget is filled; the run highlights HotelFinder, WeatherForecaster, ActivityPlanner, ItineraryBuilder as planned (node info 'planned n'), then running/done in that order; computed path = walked path = preview; RestaurantCritic untouched; no page errors. Fix found on the way: required text inputs left empty were reported as present keys (collect() keeps '' for them). Builds: patterns integration 83; default deployment 110, integration-tests 69.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run form previews the GOAP path for the inputs filled (previewGoalPath, debounced); during a run the computed path is highlighted as planned and walked step by step. For TripGoalPlanner the preview equals the path the run takes. Verified in headless Chromium in both themes and by GoalPathPreviewTest.
<!-- SECTION:FINAL_SUMMARY:END -->
