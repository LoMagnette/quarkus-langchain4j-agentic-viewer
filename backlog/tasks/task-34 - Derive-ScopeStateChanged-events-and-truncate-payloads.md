---
id: TASK-34
title: Derive ScopeStateChanged events and truncate payloads
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - live-view
milestone: m-2
dependencies:
  - TASK-31
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not a separate spec task; covers the ScopeStateChanged event and F6.3. The scope panel (T2.9) needs per-key updates, and large scope values or outputs would slow the UI and use memory.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A ScopeStateChanged event is emitted for each key written by an agent output
- [ ] #2 Every value in event payloads is truncated to a configurable size (default 2 KB) with a truncation marker
- [ ] #3 Unit tests cover truncation of strings, collections and nested objects
<!-- AC:END -->
