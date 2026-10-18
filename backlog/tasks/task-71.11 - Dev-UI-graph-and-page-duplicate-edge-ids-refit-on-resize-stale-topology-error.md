---
id: TASK-71.11
title: >-
  Dev UI graph and page: duplicate edge ids, refit on resize, stale topology
  error
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:29'
labels:
  - code-review
  - dev-ui
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 117000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
qwc-agentic-graph builds edge ids as source->target:kind; a topology contributor with two transitions between the same pair (or duplicate node ids) makes Cytoscape throw and the whole graph shows Graph failed. Every resize calls _fit(): the timeline grows row by row during a run, undoing the user zoom on each new row and when a side panel opens. qwc-agentic-systems applies a topology error of a system the user already left to the next one.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A contribution with two transitions between the same nodes renders both
- [x] #2 A user zoom or pan is kept when the graph area resizes during a run
- [x] #3 A late topology error of a previous system is not shown on the current one
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. toGraph: edge ids unique (#n suffix for further edges between the same nodes of the same kind); a contributed node id given twice is drawn once.
2. Graph: the resize observer refits only until the user changed the viewport (any viewport event not caused by _fit); reset on each draw.
3. Systems page: a topology error applies only to the system still selected.
4. Browser tests for each.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding: Cytoscape 3.34 does not throw on a duplicate edge id, it drops the second edge: the review's 'Graph failed' was wrong, the real effect was a missing transition ([1, 1] before, [2, 1] now). Browser tests aContributionWithTwoTransitionsBetweenTheSameNodesRendersBoth (graph element with a contributed planner), theUserZoomIsKeptWhenTheGraphResizes, aLateTopologyErrorIsNotShownOnTheNextSystem (getTopology rejected late through a proxy) fail on the previous JS and pass now; ui-tests profile: 17 tests pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Contributed topologies keep every transition (unique edge ids, duplicate nodes drawn once), a user's zoom or pan survives the graph area resizing during a run, and a late topology error no longer shows on the system selected since. Verified with three new browser tests (failing before) and the full ui-tests profile.
<!-- SECTION:FINAL_SUMMARY:END -->
