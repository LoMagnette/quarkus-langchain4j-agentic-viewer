---
id: TASK-22.2
title: 'T1.6b: Node shapes and edges for sequence and parallel'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:00'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies:
  - TASK-22.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-22
priority: high
type: feature
ordinal: 35000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.2). Each node kind needs a distinct shape so the structure is readable at a glance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each node kind has its own shape and a legend lists them
- [x] #2 Sequence children are linked by ordered arrows
- [x] #3 Parallel and parallel-mapper children fan out and join
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. One KIND_STYLES table (shape, border color, border style) drives both the Cytoscape style and the legend, so they cannot drift: agents are leaf shapes (AI agent round rectangle, non-AI agent cut rectangle, human in the loop round hexagon), workflows are boxes told apart by border color and style (sequence solid, parallel double, parallel mapper double, loop dashed, conditional dotted, supervisor and planner thick). Colors are fixed mid-saturation hues readable on both themes.
2. Legend above the canvas listing the kinds present in the shown system.
3. Sequence: ordered arrows between consecutive children (already there).
4. Parallel and parallel mapper: helper fork and join nodes (parentId::fork / ::join, small, not selectable) with fork -> each child -> join edges; mapper edges labelled 'each item'.
5. Verify in Chromium on ResearchGatherer, PhraseTranslator and the others (checks from T1.6a plus fork above children and join below).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
KIND_STYLES (exported) drives both the Cytoscape style and the legend: AI agent round rectangle green, non-AI agent cut rectangle slate, human in the loop round hexagon amber; workflow boxes by border: sequence solid slate, parallel double blue, parallel mapper double cyan, loop dashed violet, conditional dotted orange, supervisor and planner thick pink/fuchsia. Legend above the canvas lists the kinds present in the system. toGraph adds helper nodes (compoundId::fork / ::join, small diamonds, events off, never selectable) for PARALLEL and PARALLEL_MAPPER with fork -> child -> join edges, mapper fan-out edges labelled 'each item'; nodes are listed in drawing order (fork, children, join). ELK bk.fixedAlignment BALANCED centers the fork over its branches (default Brandes-Koepf put it above the first branch; compared with network simplex and linear segments in Node, all center). Verified in headless Chromium on all 9 samples, light and dark: containment, overlaps, declaration order, fork above/join below every branch and centered within 2px, legend kinds per system, highlight API, no page errors.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Each node kind has its own shape or border, listed in a legend; sequence children are linked by ordered arrows and parallel / parallel-mapper children fan out from a centered fork and join again. Verified on all 9 samples in headless Chromium (light and dark) with layout checks.
<!-- SECTION:FINAL_SUMMARY:END -->
