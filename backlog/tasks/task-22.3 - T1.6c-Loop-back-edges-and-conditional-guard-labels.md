---
id: TASK-22.3
title: 'T1.6c: Loop back-edges and conditional guard labels'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:05'
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
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.6 (F2.2, F2.3). Loops and conditionals carry logic that is invisible without labels.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Loops show a back-edge labelled with maxIterations
- [x] #2 Conditional branches show the guard label: the @ActivationCondition method name for declarative, "lambda" for builder
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. LOOP: back-edge from the last child to the first (self-loop when there is one child), kind BACK so ELK ignores it, dashed in the loop color and labelled 'max N' plus 'until <exit method>' when there is one; testExitAtLoopEnd shown as 'checked at end' in the label.
2. CONDITIONAL: helper fork (and join) like parallel; each fork -> branch edge labelled with the branch guard label (activation method for declarative, 'lambda' for builder, as given by the topology), 'always' when the branch has no guard.
3. Verify StoryCreator (loop) and ExpertRouter (conditional) in headless Chromium: labels present on the edges, back-edge drawn, layout checks from T1.6a-b still pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Loop: BACK edge from the last body agent to the first, labelled 'max N', then 'until <exit method>' and '(tested at the end)' when testExitAtLoopEnd, one per line; ignored by ELK. Drawn as a dashed bracket on the right of the body (Cytoscape segments, edge-distances node-position, distance computed per edge from the ELK boxes of the loop children so it clears a wider nested workflow), label right of the bracket; the loop box reserves the room on its right (paddingRight from the label length, min-width-bias-right 100% so Cytoscape puts the extra width there). One-agent loop: self-loop with the label right of it. Conditional: fork/join helpers like parallel, fork -> branch edges (kind GUARD, conditional color, italic) labelled with the topology guard label (method name for declarative, 'lambda' for builder); a branch without guard gets no label (langchain4j drops unguarded declarative branches, so 'always' would be wrong); extra layer spacing in conditional boxes for the labels. New renderedEdges() check helper. Iterated on edge styles with a standalone Cytoscape page (scratch) instead of dev-mode restarts. Verified in headless Chromium: StoryCreator back-edge inside its loop box with label 'max 5 / until goodEnough / (tested at the end)', ExpertRouter guards isMedical/isTechnical/isLegal, a synthetic system injected into the build-time data (one-agent loop, builder 'lambda' guard, unguarded branch, loop nested in a parallel) renders without errors; all T1.6a/b layout checks still pass, light and dark.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Loops show a dashed back-edge bracket labelled with maxIterations and the exit condition, and conditional branches show their guard label on the fan-out edge. Verified in headless Chromium on the samples and on a synthetic system covering a one-agent loop, a builder lambda guard, an unguarded branch and a nested loop.
<!-- SECTION:FINAL_SUMMARY:END -->
