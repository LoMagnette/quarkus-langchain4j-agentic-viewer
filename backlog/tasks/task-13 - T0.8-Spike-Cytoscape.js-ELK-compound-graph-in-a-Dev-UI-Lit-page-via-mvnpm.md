---
id: TASK-13
title: 'T0.8: Spike - Cytoscape.js + ELK compound graph in a Dev UI Lit page via mvnpm'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 09:55'
labels:
  - spike
  - dev-ui
milestone: m-0
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.8. The spec picks Cytoscape.js with ELK layout (via mvnpm) instead of the prototype Mermaid view, because nested patterns need compound nodes and the live view needs per-node highlighting. This must be proven inside a Dev UI Lit component (import maps, shadow DOM, theming).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Cytoscape.js and ELK are loaded via mvnpm dependencies in the Dev UI page
- [x] #2 A nested loop-in-sequence renders with compound nodes and ELK layout
- [x] #3 A node can be highlighted on demand from JavaScript
- [x] #4 The graph follows the Dev UI light and dark themes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. deployment depends on org.mvnpm:cytoscape 3.34.3 and org.mvnpm:elkjs 0.12.0 (Dev UI aggregates every importmap.json on the deployment class path and serves it under /q/_static). No cytoscape-elk adapter on mvnpm: compute positions with elkjs (hierarchical layered layout, INCLUDE_CHILDREN) and hand them to Cytoscape as a preset layout. elkjs maps its bare name to a CommonJS file, so load elk.bundled.js (UMD -> window.ELK).
2. Spike page "Graph (spike)" (qwc-agentic-graph-spike.js): StoryCreator fixture (sequence compound containing draft and a loop compound with score/edit and a back-edge labelled max 5); public highlight(nodeId, state) API; observeState(LitElement) + Lumo CSS variables for light/dark.
3. Verify in the sample app with headless Chromium: no browser errors, compound nesting and ELK positions, highlight on demand, screenshots in both themes.
4. The page is a spike: T1.6 replaces it with the real graph component.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Result (verified in the sample app in dev mode with headless Chromium, no failed requests, no browser errors):
- Libraries: org.mvnpm:cytoscape 3.34.3 and org.mvnpm:elkjs 0.12.0 as deployment dependencies. The Dev UI aggregates every META-INF/importmap.json found on the deployment class path and serves the jars under /q/_static (BuildTimeContentProcessor.gatherMvnpmJars). They are not in the prod app (checked quarkus-app/lib).
- No cytoscape-elk adapter exists on mvnpm: elkjs computes the positions (layered, direction DOWN, hierarchyHandling INCLUDE_CHILDREN) and Cytoscape uses them as a preset layout; compound sizes come from Cytoscape (children + padding).
- elkjs maps its bare name to lib/main (CommonJS, unusable in the browser): import 'elkjs/elk.bundled.js' instead, which registers window.ELK.
- Nested loop-in-sequence (StoryCreator fixture) renders with compound nodes: draft and polish inside create, score and edit inside polish (bounding boxes checked).
- Back-edges (loop) must be left out of the ELK layout, otherwise ELK reverses the forward edge to break the cycle (edit drawn above score); they are drawn by Cytoscape only.
- Highlight on demand: public highlight(nodeId, state) with running/done/failed, stateOf(nodeId) for checks; unknown ids return false. A "Simulate run" button steps through a run.
- Themes: the component extends observeState(LitElement) and restyles Cytoscape from Lumo CSS variables (getComputedStyle(document.body)) when themeState.theme changes; verified by toggling the Dev UI theme state (screenshots light and dark).
- Cap the initial zoom at 1: fit() zooms small graphs far above 100%.
- Cosmetic follow-ups for T1.6: the edge into a compound node ends on its label; loop forward and back edges are cramped in tight layouts.
- The "Graph (spike)" page stays in the card until T1.6 replaces it with the real component.

Validation: mvn clean install, 33 tests pass (deployment 3, integration-tests 30).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Proved Cytoscape.js + ELK in a Dev UI Lit page loaded via mvnpm: elkjs computes a hierarchical layout used as a Cytoscape preset, the nested loop-in-sequence renders with compound nodes, nodes can be highlighted on demand, and the graph follows the Dev UI light/dark theme. Spike page "Graph (spike)" (qwc-agentic-graph-spike.js) verified with headless Chromium; libraries are deployment-only.
<!-- SECTION:FINAL_SUMMARY:END -->
