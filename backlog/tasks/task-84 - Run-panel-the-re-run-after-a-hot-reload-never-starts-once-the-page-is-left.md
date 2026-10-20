---
id: TASK-84
title: 'Run panel: the re-run after a hot reload never starts once the page is left'
status: Done
assignee: []
created_date: '2026-10-20 15:39'
updated_date: '2026-10-20 16:00'
labels: []
dependencies: []
priority: high
type: bug
ordinal: 140000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (high): `_afterReload()` in `qwc-agentic-run-panel.js` retries `getTopology` for up to 20 s and then calls `_start()`. `disconnectedCallback()` only stops the event stream, so leaving the page (or turning the re-run off) during a reload can still start the agentic system, and its tools, with nobody looking.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A re-run after a reload does not start when the panel was disconnected while it waited for the system
- [x] #2 It does not start either when the re-run was turned off or another system was opened meanwhile
- [x] #3 The existing re-run after a reload still works (UI tests)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. A reload generation counter, bumped by disconnectedCallback, a system change and turning the re-run off; _afterReload takes it at the start and gives up after each await when it changed or the panel is not connected, checked again right before _start. 2. UI test: start a reload watch whose system is not found yet, remove the panel, make the system known, check no run starts. 3. UI tests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
A reload generation counter (_reloadRun) is bumped by disconnectedCallback, a system change (willUpdate) and turning the re-run off; _afterReload checks it with isConnected, the watch flag and the system after each await and right before _start. New UI test aReRunWaitingForTheSystemGivesUpWhenNobodyLooks (re-run turned off while waiting, then panel removed while waiting): fails on the old panel (1 run started), passes now. Validation: mvn -o clean install 191/106/6 (1 skipped); mvn -o clean install -Ppatterns 200 (1 skipped)/144/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The re-run after a hot reload gives up when the panel is left, another system is opened or the re-run is turned off while it waits for the system: checked after every await and just before starting. Verified with a new UI test that fails on the old code, and the full UI suite.
<!-- SECTION:FINAL_SUMMARY:END -->
