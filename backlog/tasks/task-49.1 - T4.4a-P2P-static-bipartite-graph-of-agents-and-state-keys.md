---
id: TASK-49.1
title: 'T4.4a: P2P static bipartite graph of agents and state keys'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:28'
labels:
  - patterns
  - p2p
  - dev-ui
milestone: m-4
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-49
priority: medium
type: feature
ordinal: 79000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T4.4. P2P agents activate when their inputs appear in the scope, so the structure is a bipartite agent/key graph where cycles are legitimate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The P2P planner node shows a bipartite graph of agents and state keys
- [x] #2 Cycles are rendered without breaking the layout
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Graph: a P2P planner node is drawn as a bipartite graph: its sub-agents and the state keys they read or write (inputs first, then each agent followed by the key it writes, so the layout follows the flow), key -> agent for a read, agent -> key for a write; cycles (critique/hypothesis) are left to ELK's cycle breaking.
2. Verify in the browser on ResearchTeam (dev mode -Ppatterns), light and dark.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified in the browser (scratchpad pw/p2p.mjs, dev mode -Ppatterns): ResearchTeam keys topic (input), papers, hypothesis, critique (state); edges topic->searchPapers, searchPapers->papers, topic/papers->formulate, formulate->hypothesis, hypothesis->criticize, criticize->critique, critique->refine, refine->hypothesis (the cycle); laid out left to right with ELK graph wrapping (a tall single column and an unwrapped row were both too small to read), light and dark.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A P2P planner node is drawn as a bipartite graph of its agents and the state keys they read and write, cycles included, laid out left to right and folded into rows. Verified in headless Chromium on ResearchTeam.
<!-- SECTION:FINAL_SUMMARY:END -->
