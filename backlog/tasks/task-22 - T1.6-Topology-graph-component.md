---
id: TASK-22
title: 'T1.6: Topology graph component'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:08'
labels:
  - dev-ui
  - graph
milestone: m-1
dependencies:
  - TASK-13
  - TASK-20
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.6 (F2.1 to F2.4). One graph per root system built on the T0.8 spike (Cytoscape.js + ELK). Split into subtasks by visual concern.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every sample system renders legibly
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
All four subtasks done. Parent AC verified: every sample system renders legibly, checked in headless Chromium on all 9 samples (light and dark) by a script asserting containment in parent boxes, no sibling overlap, declaration order, centered fork/join, back-edges inside their loop, and by screenshots; a synthetic system covers one-agent loops, lambda guards, unguarded branches and a loop nested in a parallel. The T0.8 'Graph (spike)' page and its script are removed. Validation: deployment 53 tests, integration-tests 35, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The Agentic Systems page shows each system as an ELK-laid-out Cytoscape graph: compound boxes for nested workflows, a shape per kind with a legend, ordered sequences, parallel/conditional fan-out with guard labels, loop back-edges with max iterations, and dashed supervisor/planner candidates; the spike page is gone. Verified in headless Chromium on all samples in both themes.
<!-- SECTION:FINAL_SUMMARY:END -->
