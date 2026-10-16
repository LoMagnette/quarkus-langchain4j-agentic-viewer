---
id: TASK-62
title: Playwright UI tests for the Dev UI
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-16 22:32'
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
- [x] #1 Playwright tests cover the system list, graph render, run form validation, live highlighting and replay of a past run
- [x] #2 Tests run in CI on the fake ChatModel
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. The Dev UI only exists in dev mode, so quarkus-playwright (@QuarkusTest) cannot reach it: use Playwright Java (com.microsoft.playwright) in a QuarkusDevModeTest of the deployment module.
2. Maven profile ui-tests in deployment: playwright + quarkus-langchain4j-openai-deployment (test scope, disabled, needed to wire a fixture ChatModel into AI agents) and test sources in src/ui-test/java; Playwright downloads Chromium on first use.
3. Fixture app: a sequence of two AI agents on a slow fixture model (so the running state can be seen), a required text input and a JSON list input.
4. Tests: system list, graph render, run form validation (required, invalid JSON, wrong type), live highlighting (running then done, output), replay of an older run, also after a page reload; no page errors.
5. CI: a step running the UI tests (with the browser's system dependencies). Docs: CLAUDE.md testing section.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Approach: Playwright Java 1.63 in a QuarkusDevModeTest (deployment, profile ui-tests, src/ui-test) since the Dev UI does not exist in @QuarkusTest, where quarkus-playwright works. The fixture app has quarkus-langchain4j-openai (disabled) as test dependency to wire its fixture ChatModel into AI agents, as integration-tests does.
Found a run form bug through the tests: after a validation error, the first click on Run after editing was lost (Vaadin validated the field on blur, hid the error, the button moved) and a second error in a row was not flagged. Fixed with manualValidation, errors cleared on edit, spacing above the button; the form test fails on the old form (mutation-checked).
Validation: AgenticSystemsPageUiTest 5/5 twice; default deployment build 118 tests green. CI step added (without-patterns configuration, Chromium installed with --with-deps); not run on GitHub (no remote), the same commands pass locally.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added Playwright browser tests of the Agentic Systems page (deployment/src/ui-test, profile ui-tests, run in CI): system list, graph render and node details, run form validation, live highlighting, replay of an older run including after a page refresh, no page script errors. They run against a QuarkusDevModeTest fixture on a fake slow echo model. They exposed a run form bug (lost click on Run after an error, unflagged second error), fixed with manual field validation. Verified: 5/5 UI tests twice, deployment default build 118 green, form test fails on the previous form.
<!-- SECTION:FINAL_SUMMARY:END -->
