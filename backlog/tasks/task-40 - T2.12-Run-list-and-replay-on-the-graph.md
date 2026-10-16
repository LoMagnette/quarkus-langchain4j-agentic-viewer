---
id: TASK-40
title: 'T2.12: Run list and replay on the graph'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:50'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-37
  - TASK-29.2
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.12 (F4.9). Runs are not persisted across restarts or hot reloads (out of scope), only kept in the bounded in-memory history.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A run list per system shows past runs with status, start time and duration
- [x] #2 Opening a past run replays its events on the graph, timeline and scope panel
- [x] #3 A past run replays after a page refresh
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run panel: 'Recent runs' list from listRuns (status, start time, duration), loaded when the system opens and refreshed when a run ends.
2. Opening a past run: getRun, then its events are replayed through the same LiveRun (graph highlights and counters) and published to the page (timeline, scope panel); the live view shows its status, output or error, inputs; a run still running is followed with streamRun (which replays) instead.
3. Cancel button while a run is running (cancelRun); a cancelled run shows as cancelled.
4. Browser check: run twice, list shows both, open the first: graph/timeline/scope show it; reload the page and reopen it.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Run panel: 'Recent runs' list from listRuns (status dot, start time, status, duration), loaded when the system opens and refreshed when a run starts and ends; the current run is highlighted. Opening a past run: getRun, graph.resetRun, its events replayed through the same LiveRun (highlights, durations, counters) and published to the page (timeline, scope panel), live view with status/output/error/inputs; a run still RUNNING is followed with streamRun (replays). Cancel button while running (cancelRun); a RunFailed with CancellationException shows as Cancelled. Rpc adapter gained listRuns/getRun/cancelRun. Runs live only in the bounded in-memory history (not persisted across restarts, out of scope). Verified in headless Chromium (scratchpad history.mjs): two StoryCreator runs listed; opening the older one shows its output 'A story about a lighthouse (draft 4)', loop ×3, 9 timeline rows and scope topic 'a lighthouse'; after a page reload the list still has both and the older one replays the same; no page errors.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run panel lists the recent runs of a system (status, start time, duration) and opening one replays its events on the graph, timeline and scope panel, also after a page refresh; running runs can be cancelled. Verified in headless Chromium.
<!-- SECTION:FINAL_SUMMARY:END -->
