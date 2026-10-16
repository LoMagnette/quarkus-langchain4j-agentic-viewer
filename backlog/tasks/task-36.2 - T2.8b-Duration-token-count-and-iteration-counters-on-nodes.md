---
id: TASK-36.2
title: 'T2.8b: Duration, token count and iteration counters on nodes'
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
dependencies:
  - TASK-36.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-36
priority: high
type: feature
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.8 (F4.1, F4.2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Finished nodes show their duration and token count
- [x] #2 Loop nodes show an iteration counter and parallel-mapper nodes an item counter
- [x] #3 The 3-iteration loop sample shows counter 3
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Graph API setNodeInfo(nodeId, text): a second label line under the node name (compound nodes: after the title).
2. Live view: finished agents show duration and tokens (summed over their invocations); loop nodes show the number of iterations (max iteration + 1 of their children); parallel-mapper nodes show finished/started items.
3. Browser check: StoryCreator loop shows ×3, agents show durations, PhraseTranslator mapper shows 3/3 items.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Graph API setNodeInfo(nodeId, text) / nodeInfo(nodeId): a second label line under an agent (leaf labels now wrap), appended to the title of a box. LiveRun shows on finished nodes their summed duration and tokens ('1.2 s · 42 tokens', tokens only when reported), on loops the iteration count (max iteration + 1 of their direct children: '×3'), on parallel mappers 'finished/started items'. Verified in headless Chromium on the sample app in dev mode (scratchpad scripts run.mjs, widgets.mjs, parallel.mjs), light and dark, no page errors. Checks: StoryCreator loop 'StyleReviewLoop ×3 · 5 ms' and agents with durations; PhraseTranslator mapper '3/3 items · 7 ms'; synthetic branch '1.2 s · 42 tokens'.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Finished nodes show their duration and tokens, loops their iteration count and parallel mappers their item count; the 3-iteration StoryCreator loop shows ×3. Verified in headless Chromium.
<!-- SECTION:FINAL_SUMMARY:END -->
