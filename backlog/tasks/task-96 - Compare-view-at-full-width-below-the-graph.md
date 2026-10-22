---
id: TASK-96
title: Compare view at full width below the graph
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 16:26'
updated_date: '2026-10-22 16:32'
labels: []
dependencies: []
priority: medium
ordinal: 152000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The owner (2026-10-22): comparing two runs (TASK-73) is a great feature but almost unusable, because the Compare tab lives in the 380px side panel: the agent, verdict and the two outputs share that width. The band below the graph (where the timeline is) spans the page width and grows with its content.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Comparing two runs shows the comparison at full width below the graph, in place of the timeline until it is closed; the side panel keeps its Run tab so other runs can be picked
- [x] #2 The comparison lays out A and B side by side with room for the outputs (fixed columns, long values wrap), and names which run is A and which is B
- [x] #3 The graph stays colored by verdict; closing the comparison brings the timeline back
- [x] #4 Browser tests of the comparison pass, and one checks it is wider than the side panel
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Page: drop the Compare side tab; render qwc-agentic-run-compare full width below the band, in place of the timeline until closed; keep the side panel as is.
2. Compare: fixed table layout with colgroups (agent 22%, verdict 11em, A and B equal; totals and inputs aligned), wrapping values, title naming A and B (start time, app tag).
3. Browser test: width > 2x the side panel, timeline replaced then back; docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Screenshot checked (scratchpad compare.png): outputs side by side and readable. Playwright 34/34 green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run comparison moved from the 380px side panel to the full-width band below the graph, in place of the timeline until closed (the Run tab stays, to pick other runs). A and B side by side in fixed columns with wrapping values, totals and inputs aligned with the agents table, title naming each run (start time, app tag). Verified by twoRunsAreComparedInvocationByInvocation (width > 2x the side panel, timeline replaced then restored) and the full browser suite (34/34).
<!-- SECTION:FINAL_SUMMARY:END -->
