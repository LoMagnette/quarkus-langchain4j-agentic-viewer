---
id: TASK-36.1
title: 'T2.8a: Highlight nodes as they start, finish or fail'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:33'
labels:
  - dev-ui
  - live-view
  - graph
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-36
priority: high
type: feature
ordinal: 57000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.8 (F4.1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The live view subscribes to streamRun and updates the graph as events arrive
- [x] #2 Running, finished and failed nodes have distinct styles
- [x] #3 Parallel branches can be highlighted as running at the same time
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. The page subscribes to streamRun after startRun and applies each event to the graph: AgentStarted -> running, AgentFinished -> done, AgentFailed -> failed; a node stays running while any of its invocations runs (parallel mapper items, overlapping parallel branches); RunFinished/RunFailed end the subscription (no completion message reaches the client).
2. Distinct running/done/failed styles (existing classes; running gets a thicker primary border), cleared when a new run starts.
3. Browser check on StoryCreator, ResearchGatherer (parallel branches seen running together by sampling the graph state while running) and a failing run.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
dev-ui/qwc-agentic-run-panel.js (form, confirmation, live view) used by the systems page in a side column with Run/Details tabs (the panel stays mounted under Details so the stream continues). After startRun it subscribes to streamRun (Dev UI JsonRpc .onNext) and feeds a LiveRun: AgentStarted -> running, AgentFinished -> done, AgentFailed -> failed, with a running count per node so a node stays running while any invocation runs (mapper items, overlapping branches); RunFinished/RunFailed end the subscription and set the status (no completion message reaches the client). Graph gained resetRun(); running style thicker primary border, done green, failed red. Gotcha: the Dev UI JsonRpc proxy cannot be passed through a Lit binding (lit-html probes _$litDirective$ and the proxy answers every property, then JSON-encodes the element): the page passes a plain adapter object. Verified in headless Chromium on the sample app in dev mode (scratchpad scripts run.mjs, widgets.mjs, parallel.mjs), light and dark, no page errors. Checks: StoryCreator, BankSupervisor, PhraseTranslator runs end with every node done; synthetic overlapping events through the panel's LiveRun show WebSearcher and DatabaseSearcher running at the same time, then failed (red) and done (green).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The live view subscribes to streamRun and highlights nodes as running, done or failed as events arrive, keeping parallel invocations of a node running until they all end. Verified in headless Chromium on sample runs and an overlapping-branches event sequence.
<!-- SECTION:FINAL_SUMMARY:END -->
