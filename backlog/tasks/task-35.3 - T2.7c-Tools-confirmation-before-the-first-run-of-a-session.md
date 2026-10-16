---
id: TASK-35.3
title: 'T2.7c: Tools confirmation before the first run of a session'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:33'
labels:
  - dev-ui
  - run
milestone: m-2
dependencies:
  - TASK-35.2
documentation:
  - spec-breakdown.md
parent_task_id: TASK-35
priority: high
type: feature
ordinal: 55000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.7 (F3.6). Runs call real tools (and possibly real models), with real side effects and cost. The user must be told before the first run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Before the first run of a session, a dialog lists every tool reachable from the system
- [x] #2 The run starts only after confirmation; cancelling starts nothing
- [x] #3 The dialog is not shown again for that system in the same session
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Tools reachable from a system: @ToolBox tools of every AI agent (name, class#method), tool sources only known at runtime, MCP client agent tools, A2A servers, from the topology.
2. Before the first run of a system in the browser session: vaadin-dialog listing them (or saying there is none) and that models are called for real; Run starts it, Cancel starts nothing; confirmed systems kept in sessionStorage so the dialog is not shown again for that system.
3. Browser check: BankSupervisor dialog lists credit, withdraw, exchange; cancel starts no run; second run no dialog.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Exported reachableTools(system): @ToolBox tools of every AI agent (name, Class#method), tools only known at runtime (suppliers, MCP clients), MCP client agent tools and A2A servers, from the topology. Before the first run of a system in the session a vaadin-dialog ('Run <system>?') says that models are called and tools run for real and lists them (or 'No tool is reachable'); Cancel starts nothing, Run starts it and marks the system confirmed in sessionStorage. Verified in headless Chromium on the sample app in dev mode (scratchpad scripts run.mjs, widgets.mjs, parallel.mjs), light and dark, no page errors. Checks: StoryCreator dialog (no tool), cancel leaves the form and no run, confirm runs; BankSupervisor dialog lists exchange ExchangeTool#exchange, credit BankTool#credit, withdraw BankTool#withdraw; re-runs show no dialog.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Before the first run of a system in a session, a dialog lists every tool the run can reach and warns of real calls; only confirmation starts the run and it is not asked again for that system. Verified in headless Chromium on StoryCreator and BankSupervisor.
<!-- SECTION:FINAL_SUMMARY:END -->
