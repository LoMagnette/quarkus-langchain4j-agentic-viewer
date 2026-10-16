---
id: TASK-22.1
title: 'T1.6a: Graph component with compound nodes and ELK layout'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:57'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.1). Nested patterns are drawn as compound nodes, e.g. a loop inside a sequence is a box inside a box.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A Lit component renders an AgenticSystem topology with nested patterns as compound nodes
- [x] #2 Layout uses ELK and stays readable for every sample system
- [x] #3 The component exposes an API to highlight a node by id (used by the live view)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. New dev-ui/qwc-agentic-graph.js (Lit, observeState for theme like the spike): property system (AgenticSystem JSON from the agenticSystems build-time data); a pure, exported toGraph(system) turns the topology into graph nodes/edges: workflow nodes with children become Cytoscape compound nodes (parent of their children), agents and childless workflows are leaves; node ids are the topology node ids, synthetic helper nodes (later fork/join/hub) use parentId + '::' + role so they never collide.
2. Layout with elkjs layered DOWN, INCLUDE_CHILDREN, positions as a Cytoscape preset (spike approach); back-edges left out of the layout. Edges in this subtask: sequence order between siblings so the layout reads top-down; per-kind edges come in T1.6b-d.
3. Public API kept from the spike: highlight(nodeId, state) running/done/failed, clearHighlights(), stateOf(nodeId), renderedNodes(); event 'node-selected' with the node id for T1.7.
4. qwc-agentic-systems.js system view shows the graph instead of the outline. The spike page stays until TASK-22 closes.
5. Verify in the sample app with headless Chromium: every sample system renders, compound boxes contain their children (bounding boxes), no overlaps, highlight API works; screenshots light/dark.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-13 (T0.8 spike, qwc-agentic-graph-spike.js): elkjs computes positions (layered DOWN, INCLUDE_CHILDREN) for a Cytoscape preset layout; import elkjs/elk.bundled.js (window.ELK); exclude loop back-edges from the ELK graph; cap initial zoom at 1; restyle from Lumo variables via observeState + themeState. Known cosmetic issues: edges into compound nodes end on the label; tight loop edges overlap.

New dev-ui/qwc-agentic-graph.js: exported toGraph(system) (topology -> nodes/edges; a node with children is a compound node, ids are topology ids, helper nodes will use parentId + '::' + role) and the qwc-agentic-graph Lit component (property system; observeState theme restyle; events node-selected {nodeId} and graph-rendered; API highlight/clearHighlights/stateOf/renderedNodes, layoutDone flag). Mounted in the qwc-agentic-systems system view in place of the T1.5 outline; KIND_LABELS now exported by the graph module. Layout findings: (1) elkjs hierarchical layout (INCLUDE_CHILDREN) ignores model order, siblings came out sorted by id; laying out each compound separately (ELK default) with considerModelOrder NODES_AND_EDGES, forceNodeModelOrder and separateConnectedComponents=false, edges declared on the compound that owns both ends, keeps declaration order (all edges link siblings). A considerModelOrder option set only on a compound crashes elkjs 0.12 (TypeError in tlc). (2) Edge into a compound crossed its title (spike follow-up): compounds now take their ELK size through min-width/min-height (minus Cytoscape padding) with min-height-bias-top 100%, so the title sits inside the box and edges stop at the border; compound-sizing-wrt-labels include does not work for titles above the box. Verification (headless Chromium, sample app in dev mode, light and dark): all 9 systems render; every node inside its parent box, no overlap between siblings, siblings in declaration order (script checks renderedNodes), highlight API ok (unknown id -> false), no page errors. Dev mode does not reload a rebuilt extension jar: restart it after changes. The spike page stays until TASK-22 closes.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A reusable qwc-agentic-graph component renders an AgenticSystem with nested workflows as compound nodes, laid out by ELK in declaration order with titles inside boxes, and exposes highlight/stateOf for the live view; the system view of the Agentic Systems page now shows it. Verified on all 9 sample systems in headless Chromium (containment, overlaps, order, highlight API, light and dark).
<!-- SECTION:FINAL_SUMMARY:END -->
