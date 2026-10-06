---
id: TASK-13
title: 'T0.8: Spike - Cytoscape.js + ELK compound graph in a Dev UI Lit page via mvnpm'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - spike
  - dev-ui
milestone: m-0
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.8. The spec picks Cytoscape.js with ELK layout (via mvnpm) instead of the prototype Mermaid view, because nested patterns need compound nodes and the live view needs per-node highlighting. This must be proven inside a Dev UI Lit component (import maps, shadow DOM, theming).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Cytoscape.js and ELK are loaded via mvnpm dependencies in the Dev UI page
- [ ] #2 A nested loop-in-sequence renders with compound nodes and ELK layout
- [ ] #3 A node can be highlighted on demand from JavaScript
- [ ] #4 The graph follows the Dev UI light and dark themes
<!-- AC:END -->
