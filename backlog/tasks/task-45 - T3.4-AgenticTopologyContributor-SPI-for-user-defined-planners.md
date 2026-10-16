---
id: TASK-45
title: 'T3.4: AgenticTopologyContributor SPI for user-defined planners'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 19:49'
labels:
  - runtime
  - spi
  - planner
milestone: m-3
dependencies:
  - TASK-44
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.4 (F5 custom planner row). A custom planner may have structure the generic view cannot infer. An optional SPI in `runtime` lets users describe its topology; it must stay a pure interface so prod builds carry no behaviour.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An AgenticTopologyContributor interface in runtime lets a planner contribute nodes and edges to its planner node
- [x] #2 Contributors are discovered and applied in dev mode only
- [x] #3 The sample custom planner draws its own topology through the SPI
- [x] #4 The SPI is documented with an example
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Design (recommended, user: continue): a contributor class per planner agent, discovered at build time, instantiated in dev mode only.
1. runtime (inert): spi.AgenticTopologyContributor { Class<?> plannerAgent(); TopologyContribution contribute(AgentNode plannerNode); } with records TopologyContribution(nodes, edges), ContributedNode(id, label), ContributedEdge(source, target, label); sources/targets name a sub-agent (agent name or node id) or a contributed node.
2. deployment (dev only): Jandex implementors of the interface recorded as class names into AgenticViewerRecorder.
3. runtime-dev: TopologyContributions instantiates them (no-arg constructor, TCCL), matches PLANNER nodes by the class of their @PlannerSupplier (plannerInfo.extras.supplier), resolves references to node ids (contributed nodes get <plannerNodeId>::<id>), logs and skips bad ones; JSON-RPC getContributions(systemId) -> Map<nodeId, contribution>.
4. UI: the page attaches contributions to the system; toGraph draws contributed nodes inside the planner compound and contributed edges (labelled, solid) next to the candidate edges.
5. Sample: DraftReviewTopology for DraftReviewer (writeDraft -> review 'draft', review -> writeDraft 'rejected', review -> approved end node). Tests: deployment dev-mode JSON-RPC test with a fixture planner + contributor, integration check of the sample; browser check. Javadoc example (docs page is TASK-64).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design change during implementation: a contribution replaces the generic hub and candidate edges of its planner node (drawn together they crossed and overlapped); step numbers of a run then go on the nodes (graph._label), leaves grow by a line and compounds widen to their title. Verified: TopologyContributorDevModeTest (dev mode, JSON-RPC getContributions: references by agent name / node id / contributed id resolved, unknown end dropped, failing contributor and another planner's contributor ignored; unknown system is an error); AgenticViewerOutsideDevModeTest unchanged (no Dev UI outside dev mode). Browser (scratchpad pw/contrib.mjs, planner.mjs on the ollama dev mode): DraftReviewer drawn as writeDraft -draft-> review -rejected-> writeDraft, review -approved-> 'approved or 3 drafts'; legend 'Planner state'; tapping a contributed node opens nothing; a run shows steps 1, 3, 5 / 2, 4, 6 on the nodes, live and replayed, light and dark; supervisor, loop and mapper views unchanged. Builds: deployment 99, integration-tests 68.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Applications can describe a custom planner's structure with an AgenticTopologyContributor (runtime SPI: interface + records, javadoc example): found at build time, applied in dev mode only to the planner nodes of the named @PlannerAgent, served by getContributions and drawn in place of the generic candidates; the DraftReviewer sample draws its review loop through it. Verified by TopologyContributorDevModeTest, the outside-dev-mode test, browser checks and the full build.
<!-- SECTION:FINAL_SUMMARY:END -->
