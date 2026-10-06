---
id: TASK-26
title: Retire the prototype Mermaid view and ASM imperative scanner
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - cleanup
  - dev-ui
milestone: m-1
dependencies:
  - TASK-21
  - TASK-22
documentation:
  - spec-breakdown.md
priority: low
type: chore
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not in the spec task list. The prototype renders Mermaid diagrams (`qwc-agentic-viewer.js`) and detects builder calls with an ASM scanner (`ImperativeAgentScanner`). The spec replaces both: Cytoscape graph (T1.6) and runtime builder discovery (Phase 5). Keeping both views would confuse users and double maintenance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Mermaid page, ImperativeAgentScanner and the prototype-only model records are removed
- [ ] #2 Any prototype behaviour still needed is covered by a spec task
- [ ] #3 The build and all tests pass
<!-- AC:END -->
