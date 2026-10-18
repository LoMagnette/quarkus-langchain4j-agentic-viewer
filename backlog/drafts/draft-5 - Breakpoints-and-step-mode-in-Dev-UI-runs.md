---
id: DRAFT-5
title: Breakpoints and step mode in Dev UI runs
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
labels:
  - idea
  - dev-ui
  - runs
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Pause a Dev UI run before chosen nodes (or before every agent in step mode), inspect and edit the AgenticScope state, then continue, skip the agent or abort. Feasibility: reuses the waiting state, answer box and RunManager.awaitHumanInput of the human-in-the-loop support; needs scope writes from the Dev UI and a breakpoint toggle on graph nodes.
<!-- SECTION:DESCRIPTION:END -->
