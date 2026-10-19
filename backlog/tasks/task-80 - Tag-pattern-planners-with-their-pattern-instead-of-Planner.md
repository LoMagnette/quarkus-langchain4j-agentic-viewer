---
id: TASK-80
title: Tag pattern planners with their pattern instead of Planner
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 16:53'
updated_date: '2026-10-19 17:25'
labels: []
dependencies: []
priority: low
type: enhancement
ordinal: 136000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A planner node built on one of the langchain4j-agentic-patterns planners (GOAP, peer-to-peer, blackboard, BDI, debate, voting) is tagged 'Planner' everywhere in the Dev UI, like a custom planner: the pattern only shows in the node details. The user asked that such nodes be tagged with the actual pattern, so a system's shape reads at a glance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The group title of a pattern planner in the graph names its pattern (e.g. 'BDI · ReleaseCaptain'); a custom planner keeps 'Planner'
- [x] #2 The kind badge of the node details and the kind tag of the timeline rows name the pattern for a pattern planner
- [x] #3 The Patterns column of the system list shows the pattern of each planner of a system instead of a single 'Planner' (a custom planner still shows 'Planner')
- [x] #4 Covered by tests: system summary (Java) and Dev UI (browser test)
- [x] #5 The graph legend names the pattern of each pattern planner drawn (custom planners: 'Planner')
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. qwc-agentic-graph.js: export PATTERN_TAGS (GOAP, Peer-to-peer, Blackboard, BDI, Debate, Voting) and kindLabel(kind, patternType); use it in the compound label (legend stays per kind: it explains shapes and colors).
2. Node details badge and timeline kind tag (the timeline keeps the node per id) use kindLabel, the kind ('Planner') in the tag's title.
3. AgenticSystemSummary: new plannerPatterns component (pattern of each PLANNER node in tree order, no duplicates, CUSTOM when unknown); the system list expands the PLANNER chip into them.
4. Tests: PatternPlannerTypesTest (summaries of the six pattern samples), PlannerTypesTest (custom planner keeps CUSTOM, no planner gives an empty list), AgenticSystemsDevModeTest (JSON-RPC field), new UI test patternPlannersAreTaggedWithTheirPattern (graph title, details badge, timeline tag, system list chips). Docs: one sentence in Pattern views.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The graph legend keeps 'Planner': it explains the shape and color of a kind, the same for every planner. A system with a pattern planner and a custom one lists both tags (e.g. BDI, Planner).
Validation: mvn -o clean install (deployment 185, integration-tests 106, ITs 6 / 1 skipped), -Ppatterns (194 / 1 skipped, 139, 6 / 1 skipped), UI tests 31/31 including the new one.

User clarification (2026-10-19): the pattern name wherever it is known, 'Planner' only when it is not. The graph legend was the one place left showing 'Planner' for a pattern planner: it now lists one entry per pattern (same planner swatch), and 'Planner' for custom ones. UI tests 31/31 (patternPlannersAreTaggedWithTheirPattern also checks the legend).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Pattern planners (GOAP, peer-to-peer, blackboard, BDI, debate, voting) are tagged with their pattern instead of 'Planner' wherever a kind is shown: graph group title and legend, node details badge, timeline tag (shared kindLabel in qwc-agentic-graph.js, 'Planner' kept in the tag's title), and the system list's Patterns column through a new AgenticSystemSummary.plannerPatterns. Planners whose pattern is unknown keep 'Planner'. Verified by the summary tests (pattern samples, custom sample, JSON-RPC) and a browser test covering the five places; default, patterns and UI builds pass.
<!-- SECTION:FINAL_SUMMARY:END -->
