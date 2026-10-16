---
id: TASK-21
title: 'T1.5: Dev UI card and system list page'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:42'
labels:
  - dev-ui
milestone: m-1
dependencies:
  - TASK-19
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.5. Entry point of the Dev UI: the user picks a system to inspect or run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The card shows the number of discovered systems
- [x] #2 The list page shows each root system with name, origin and pattern kinds
- [x] #3 Selecting a system opens its graph page
- [x] #4 An empty state is shown when no agentic system is found
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Card: the 'Agentic Systems' page link carries the number of systems as its static label (Dev UI card badge); the prototype pages stay until TASK-26. Builder systems (Phase 5) will need a dynamic label once JSON-RPC lists them (T2.1).
2. qwc-agentic-systems.js reads the agenticSystems build-time data (T1.4) instead of the prototype JSON-RPC count: a vaadin-grid with name, origin, pattern kinds (distinct workflow kinds of the tree in order; 'single agent' when none) and source class#method.
3. Selecting a row opens the system view inside the same page (back button): header plus a nested outline of the topology (kind, name, guard, max iterations). T1.6 puts the graph component in this view; navigating between separate Dev UI pages with a parameter is not supported by the card page model, so the list and the system view share one page.
4. Empty state when no system is found.
5. Verify in the sample app in dev mode with headless Chromium (list of 9 systems, card label, selection, back) and the empty state in a dev-mode test app without agents; build-time label checked by a dev-mode test.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Card: the 'Agentic Systems' page link carries the system count as staticLabel (Dev UI stores it with a leading space); covered by AgenticSystemsDevModeTest.cardShowsTheNumberOfSystems through the devui 'extensions' build-time data. The count is build-time only (declarative); builder systems (Phase 5) will need a dynamic label via JSON-RPC (T2.1). qwc-agentic-systems.js now reads the agenticSystems build-time data: vaadin-grid (name, origin, pattern kinds = distinct workflow kinds in tree order or 'single <agent kind>', source Class#method). Selecting a row opens the system view in the same page (back button) with a nested outline: kind, name, output key, loop exit guard and max iterations, activation guard, planner supplier. Dev UI card pages cannot pass a parameter to another page, so T1.6 should mount the graph component in this system view (outline can stay as a fallback or go). Browser verification (headless Chromium via Playwright, sample app in dev mode): card badge 9; grid lists the 9 sample systems with origin and pattern kinds (e.g. ExpertRouter Sequence+Conditional, StoryCreator Sequence+Loop, TripPlanner Sequence+Human in the loop); StoryCreator, ExpertRouter (when isMedical/isTechnical/isLegal), DraftReviewer (planner from DraftReviewer#planner) and BankSupervisor outlines correct; back returns to the list; empty state checked by serving an empty agenticSystems build-time data module; no page errors (blob: ERR_ABORTED requests also happen on other Dev UI pages). The prototype qwc-agentic-card.js is not registered as custom card and stays until TASK-26. Validation: deployment 53 tests, integration-tests 35, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The Dev UI card shows the number of agentic systems, and the Agentic Systems page lists every root system (name, origin, pattern kinds, source) from the build-time topology, opens a system view with its nested structure, and shows an empty state when there is none. Verified in the sample app with headless Chromium (list, selection, back, empty state via intercepted data) and by a dev-mode test for the card label.
<!-- SECTION:FINAL_SUMMARY:END -->
