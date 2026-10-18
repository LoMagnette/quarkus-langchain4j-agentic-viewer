---
id: DRAFT-3
title: 'Run part of a system: run only this node, or run from here'
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
Developers iterating on one agent of a long workflow must re-run (and pay for) everything before it. From a node: run only that sub-agent with its input keys pre-filled from the scope of a past run at that point, or run the rest of a sequence from it, the agents before it served from the recorded outputs of a past run. Feasibility: the invoker swap of HumanInTheLoopSteps can serve recorded outputs. Goes with the pinned outputs idea (same mechanism). Open questions: parallel/loop/supervisor nodes, which past run to seed from, scope keys written by skipped agents.
<!-- SECTION:DESCRIPTION:END -->
