---
id: TASK-59
title: 'T6.1: Config options: enabled, history size, truncation'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - config
  - runtime-dev
milestone: m-6
dependencies:
  - TASK-29.2
  - TASK-34
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 94000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.1 (F6.1, F6.3). Consolidates every option under `quarkus.langchain4j.agentic.dev-ui.*` with docs and tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 quarkus.langchain4j.agentic.dev-ui.enabled=false removes the card, JSON-RPC service, listener and decorators
- [ ] #2 History size per system and payload truncation size are configurable
- [ ] #3 Every option is documented in the generated config reference and covered by a test
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
builder-discovery option moved to T5.1 (TASK-52) with builder support deferred (decision-2).
<!-- SECTION:NOTES:END -->
