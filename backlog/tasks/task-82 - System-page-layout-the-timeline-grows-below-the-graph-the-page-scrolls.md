---
id: TASK-82
title: 'System page layout: the timeline grows below the graph, the page scrolls'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 18:28'
updated_date: '2026-10-19 18:43'
labels: []
dependencies: []
priority: medium
type: enhancement
ordinal: 138000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
With many timeline rows (e.g. the FeatureDelivery sample, 1440x900), the graph draws over the timeline: the timeline is capped at 35% of the page height and the graph needs at least 480px. The user also wants the timeline of a large system's run to extend down instead of scrolling inside a box. Layout chosen by the user (2026-10-19): the page scrolls; graph and side panel share a band at the top whose height is resizable (default: the window below the header, remembered per browser); the timeline sits below both at full width and shows every row.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The graph never overlaps the timeline, whatever the number of rows
- [x] #2 The timeline spans the full page width below the graph and the side panel, shows every row without its own scroll, and the page scrolls
- [x] #3 The graph band fills the window below the header by default; a drag handle resizes it, double-click restores the default, and the height is remembered per browser (storage failures ignored)
- [x] #4 The side panel keeps the band's height and scrolls inside it
- [x] #5 Covered by a browser test; existing browser tests pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. qwc-agentic-systems.js: :host scrolls (overflow-y auto); system view = header, band (.system-body: graph column + side, fixed height), resize handle, then the timeline at full width (no max-height).
2. Band height: user height from localStorage (try/catch) or the default measured from the host (window height below the band's top, at least 360px), recomputed on host resize while no user height; pointer drag on the handle (min 320px), double-click resets.
3. qwc-agentic-graph.js: min-heights lowered so the band decides (the graph already resizes Cytoscape on size changes).
4. Browser test with many timeline rows: no overlap (bounding boxes), timeline below band and full width, no inner scroll, page scrolls, drag changes and persists the height, double-click resets. Screenshot check with FeatureDelivery.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Reproduced with FeatureDelivery at 1440x900 (timeline capped at 35%, graph min-height 480px: the graph drew over the timeline). Implemented as planned: the qwc-agentic-systems host scrolls; band (.system-body) sized by _applyBandHeight (user height or the window below the band's top, min 360), host ResizeObserver; .band-resizer (pointer drag, min 320px, ArrowUp/Down by 40px, double-click resets; role=separator) stores the height in localStorage (try/catch); timeline moved below the band, no max-height; graph min-heights lowered (200px). Screenshots (top / bottom / dragged) checked: no overlap, full-width timeline, page scrolls, side panel scrolls inside the band.
Validation: mvn -o clean install (deployment 185, integration-tests 106, ITs 6 / 1 skipped); UI tests 32/32 (new theTimelineGrowsBelowTheGraphAndThePageScrolls; theUserZoomIsKeptWhenTheGraphResizes still passes). A first UI run had liveHighlighting time out waiting for the system list, then the following tests error with 'Could not find resource ...UiTest.class' (dev-mode test harness); the three first failing tests pass alone and the full suite passed 32/32 on the rerun.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The system page now scrolls: the graph and the side panel share a band at the top that fills the window below the header (or the height dragged on a handle under it, remembered per browser, double-click for the default), and the timeline sits below both at full width and shows every row, so it grows with the run instead of overlapping the graph. Verified with screenshots of a FeatureDelivery run and a browser test (no overlap, full width, no inner scroll, page scrolls, drag + persistence + reset); default build and 32/32 UI tests pass. Docs and CLAUDE.md updated.
<!-- SECTION:FINAL_SUMMARY:END -->
