---
id: TASK-37.1
title: 'T2.9a: Waterfall timeline of invocations'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:41'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-33
documentation:
  - spec-breakdown.md
parent_task_id: TASK-37
priority: high
type: feature
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.9 (F4.2, F4.3). The timeline nests invocations by parent, the way AgentMonitor records them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every invocation is listed with start offset and duration bars, nested by parent invocation
- [x] #2 Each loop iteration and parallel-mapper item has its own row
- [x] #3 The timeline matches AgentMonitor output for the same sample run
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. dev-ui/qwc-agentic-timeline.js: exported buildTimeline(events) -> rows depth-first by parent invocation (AgentStarted.parentInvocationId), ordered by start: invocation id, node id, label (agent name, 'iteration n' / 'item n'), start offset from RunStarted, duration (live: up to now while running), status; the component draws a waterfall (indent by depth, bar from offset over the run's total span, duration text), re-rendered while running.
2. The run panel fires its events to the page (run-updated); the page shows the timeline under the graph once a run exists (graph column split vertically).
3. AgentMonitor comparison: integration test attaching an AgentMonitor to the sample roots next to the Dev UI listener; for each sample run, the canonical tree (agent name, iteration, nested invocations, tool names) from AgentMonitor's execution for the run's memory id equals the one built from our events by parent invocation.
4. Browser check: StoryCreator rows (3 iterations), PhraseTranslator items, nesting and bars.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
dev-ui/qwc-agentic-timeline.js: exported buildTimeline(events, now) -> rows depth-first by parent invocation (AgentStarted.parentInvocationId), children by start (parallel-mapper items by item index), with start offset from RunStarted, span (up to now while running), status, iteration/item, tokens; the component draws a waterfall (indent by depth, bar left/width over the run span, status colors, duration), ticking every 250 ms while running. The run panel fires run-updated (events, running); the page shows the timeline under the graph (graph column split vertically). AgentMonitor oracle: integration-tests TimelineMatchesAgentMonitorTest attaches an AgentMonitor (setMaxRetainedSessions 1000) to the sample roots next to the Dev UI listener and, for each of the 9 samples, compares the AgentMonitor execution of the run's memory id (agent names, nested invocations, tool executions) with the nesting built from our events by parent invocation, siblings in a normalized order (parallel callbacks may reach the two listeners in different orders): all equal, e.g. create[draft, polish[3 x edit, 3 x score]], translateAll[translate_0..2], transact[credit[tool:credit], exchange[...], withdraw[...]]. Verified in headless Chromium on the sample app in dev mode (scratchpad timeline.mjs), light and dark, no page errors. Rows: StoryCreator create > draft, polish > score/edit iterations 1-3; PhraseTranslator translateAll > translate_0..2 items 1-3.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A waterfall timeline under the graph lists every invocation with start offset and duration bars, nested by parent, one row per loop iteration and mapper item, and matches AgentMonitor's record of the same run for every sample. Verified by an AgentMonitor comparison test and headless Chromium checks.
<!-- SECTION:FINAL_SUMMARY:END -->
