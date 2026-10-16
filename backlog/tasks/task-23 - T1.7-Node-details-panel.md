---
id: TASK-23
title: 'T1.7: Node details panel'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:12'
labels:
  - dev-ui
milestone: m-1
dependencies:
  - TASK-22
  - TASK-18.4
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.7 (F2.5). Clicking a node shows what the agent is and does without opening the code.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Clicking a node opens a panel with description, input keys, output key, optional and async flags
- [x] #2 For AI agents the panel also shows prompts, model name and tools
- [x] #3 All fields are shown for an AI agent sample
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. New dev-ui/qwc-agentic-node-details.js (Lit): property node (topology AgentNode JSON); sections: header (name, kind, id), description, input keys, output key, optional/async flags, guard (loop exit, max iterations, tested at end / activation condition), planner supplier; for AI agents: model (or 'default model'), system and user message templates (pre-wrap, scrollable), tools (name, Class#method, description) and tools only known at runtime. Missing values shown as 'none', so every field is visibly present.
2. qwc-agentic-systems system view: graph and panel side by side; the graph's node-selected event selects the node (looked up by id in the topology), a close button clears it; selecting another system clears it.
3. Graph: selected node outlined (Cytoscape :selected style, single selection); clicking the background clears the selection (event with null id).
4. Verify in headless Chromium: click BankSupervisor's CreditAgent (system message, user message, ToolBox tools) and check every field, a workflow node (loop guard on StoryCreator), close, background click; light and dark screenshots.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
New dev-ui/qwc-agentic-node-details.js (property node, event close): name, kind badge in the kind color, node id; description, input keys, output key, optional, async; loop exit condition, max iterations and when the exit is tested ('at the end of an iteration' / 'after each agent'); activation condition of a conditional branch; planner supplier; for AI agents model ('default model' when none), system and user message templates (pre-wrap, scrollable), tools (name, Class#method, description), tools known only at runtime. Empty values show 'none' so every field is visibly present. Systems page: graph and panel side by side (panel 360px), node-selected -> node looked up in the topology; close button and background click clear panel and graph selection; changing system clears it. Graph: :selected overlay, background tap emits node-selected with null, clearSelection(), ResizeObserver resizes and refits the canvas when the panel opens (graph was cut off before). Verified in headless Chromium by clicking rendered nodes: BankSupervisor CreditAgent shows description, keys, flags, default model, system and user messages and both BankTool tools (credit, withdraw) with descriptions; StoryCreator loop shows goodEnough / 5 / at the end of an iteration; ExpertRouter medical shows activation isMedical and 'none' system message/tools; DraftReviewer planner shows 'from DraftReviewer#planner'; selection outlined, cleared by background click and close; all nodes in view after the refit; light and dark; no page errors; graph layout checks still pass on the 9 samples.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Clicking a graph node opens a details panel with its description, keys, flags, guard and planner, plus prompts, model and tools for AI agents; the node is outlined and the graph refits beside the panel. Verified in headless Chromium on an AI agent sample with every field (BankSupervisor credit agent) and on loop, branch and planner nodes, light and dark.
<!-- SECTION:FINAL_SUMMARY:END -->
