---
id: TASK-24
title: 'T1.8: Data-flow view toggle based on scope keys'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:19'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies:
  - TASK-22
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.8 (F2.6). Agents communicate through AgenticScope keys rather than direct calls; a data-flow view shows which agent produces what another consumes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A toggle switches between the structure view and a data-flow view
- [x] #2 In the data-flow view, keys are shown as links from the writing agent to the reading agents
- [x] #3 Keys read but never written (system inputs) are visually distinguished
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Exported toDataFlowGraph(system): flat graph of the agents (leaf nodes: AI, non-AI, human in the loop, childless workflows) in tree order, same node ids as the structure view so selection, details and highlight keep working; one DATA edge per key from every agent writing it (outputKey) to every other agent reading it (inputKeys), labelled with the key; a key read but never written becomes an input node (key::<name>, pill shape, dashed, not selectable) with INPUT edges to its readers.
2. Graph component: property view ('structure' | 'dataflow') and a toggle in its toolbar next to the legend; legend adapted (system input entry); ELK cycle breaking by model order for the data-flow view (loops create cycles).
3. Verify in headless Chromium on StoryCreator (loop: story written by draft and edit, read by score and edit), ExpertRouter, TripPlanner and the others: toggle switches views, edges labelled with keys between writer and reader, system inputs distinguished, selection and details still work in the data-flow view, no page errors, light and dark.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Graph component: property view ('structure' | 'dataflow') with a Structure / Data flow toggle in its toolbar; legend lists the kinds drawn in the current view (System input entry in data flow). Exported toDataFlowGraph(system): the agents (childless nodes) with the topology ids, so selection, details and highlight keep working; edges from each writer (outputKey) to each other reader (inputKeys), merged per (source, reader) with the keys listed one per line. A key no agent writes comes from: the caller when it is a root method parameter (system input node key::<name>, dashed tag, not selectable); the items of an enclosing parallel mapper ('phrase <- each phrases', from the sources of the mapper's input keys); an enclosing supervisor, which passes arguments to the agents it calls (supervisor node, dashed pink ARGUMENT edges, its own inputs drawn too); otherwise an unknown origin, drawn as a system input. Layout: edges with ELK label sizes, ORTHOGONAL routing drawn along ELK routes (end points relative to node centers, bend points converted to Cytoscape segments weights/distances); model-order cycle breaking with system inputs and suppliers listed first so sources stay on top. Gotcha: Cytoscape ignores curve-style in an element's initial style bypass (weights/distances apply), so the curve style comes from classes (routed / routed-straight). Verified in headless Chromium on all 9 samples, light and dark: StoryCreator topic -> draft, story draft -> score, edit -> score, draft -> edit; BankSupervisor request -> transact, transact -> exchange (originalCurrency, amount, targetCurrency), withdrawUser, creditUser, amountInUSD exchange -> withdraw/credit; PhraseTranslator phrases -> translate (phrase <- each phrases); TripPlanner wish/destination/approval; no overlapping nodes; clicking an agent in the data-flow view opens its details; back to structure restores compound nodes; structure checks, details checks and refit still pass; no page errors.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A Structure / Data flow toggle switches the graph to a view where agents are linked by the AgenticScope keys they write and read, routed by ELK, with system inputs drawn as distinct input nodes and keys supplied by a parallel mapper or a supervisor attributed to them. Verified in headless Chromium on all samples in both themes, with selection and details working in both views.
<!-- SECTION:FINAL_SUMMARY:END -->
