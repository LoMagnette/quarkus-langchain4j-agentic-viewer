---
id: TASK-97
title: Compare runs in a popup
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 17:51'
updated_date: '2026-10-22 18:00'
labels: []
dependencies: []
priority: medium
ordinal: 153000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The owner (2026-10-22), after TASK-96 moved the comparison to the full-width band: would it be possible in a popup. A modeless, draggable dialog keeps the graph (colored by verdict) and the timeline visible behind it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Comparing two runs opens the comparison in a large dialog that does not block the page (the graph colored by verdict stays visible and usable) and can be moved
- [x] #2 Closing the dialog (Close, Escape) ends the comparison and clears the graph colors; the timeline stays in place
- [x] #3 Browser tests of the comparison pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Page: render qwc-agentic-run-compare in a vaadin-dialog (modeless, draggable, resizable, header "Compare runs"), opened over the graph column (top/left/width/height from its bounding box; a new comparison keeps where the dialog was put); the timeline stays; Close / Escape end the comparison and clear the graph colors.
2. Browser test: dialog wider than the side panel and not covering it, resizable, Escape closes; docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
First try (centered, 88vw) covered the run panel: a history row click was intercepted, so the dialog is placed over the graph column instead. Vaadin 25.2 (Dev UI) supports top/left/width/height/resizable. Screenshot checked (scratchpad popup.png). Playwright 34/34.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run comparison opens in a modeless, draggable, resizable dialog placed over the graph column, so the run panel (history) stays usable beside it and the timeline below; Close or Escape end it and clear the graph colors. Verified by twoRunsAreComparedInvocationByInvocation (wider than the side panel without covering it, a history row clickable meanwhile, Escape) and the browser suite 34/34.
<!-- SECTION:FINAL_SUMMARY:END -->
