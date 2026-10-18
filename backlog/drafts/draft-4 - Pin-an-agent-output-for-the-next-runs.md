---
id: DRAFT-4
title: Pin an agent output for the next runs
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
Freeze the output of an agent (taken from a past run or typed) so that Dev UI runs return it without calling the model: fast and cheap iteration on downstream prompts. Pinned nodes are marked on the graph and in the timeline; pins are per system, kept for the dev session, cleared on demand. Feasibility: same invoker interception as HumanInTheLoopSteps; only in Dev UI runs, never for application calls.
<!-- SECTION:DESCRIPTION:END -->
