---
id: TASK-62
title: Playwright UI tests for the Dev UI
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
labels:
  - testing
  - dev-ui
milestone: m-6
dependencies:
  - TASK-40
  - TASK-35
documentation:
  - spec-breakdown.md
priority: medium
type: task
ordinal: 97000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the spec testing strategy: UI tests with Playwright (quarkus-playwright) for list, graph render, run form, live highlighting and replay. Prevents regressions in the Lit/Cytoscape front end that unit tests cannot catch.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Playwright tests cover the system list, graph render, run form validation, live highlighting and replay of a past run
- [ ] #2 Tests run in CI on the fake ChatModel
<!-- AC:END -->
