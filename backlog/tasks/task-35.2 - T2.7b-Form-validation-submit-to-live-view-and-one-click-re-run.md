---
id: TASK-35.2
title: 'T2.7b: Form validation, submit to live view and one-click re-run'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:33'
labels:
  - dev-ui
  - run
milestone: m-2
dependencies:
  - TASK-35.1
  - TASK-29.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-35
priority: high
type: feature
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.7 (F3.3 to F3.5).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Inputs are validated against the JSON schema before submit; missing required keys and invalid values are flagged per field
- [x] #2 Submitting calls startRun and switches to the live view of that run
- [x] #3 The last inputs used per system are kept for the browser session and a run can be repeated in one click
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Validation in the form against the JSON schema subset the generator emits (type, enum, required, properties, items, additionalProperties, min/maxLength, uniqueItems): JSON editors are parsed first; errors shown on their field; submit blocked while invalid.
2. Submit fires the inputs; the page calls startRun and switches the run panel to the live view of that run.
3. Last inputs per system in sessionStorage (try/catch), pre-filled next time; 'Run again' repeats the last run in one click.
4. Browser check: missing required value and invalid JSON flagged, run started, re-run.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Exported validate(schema, value) for the generated subset (type, enum, required, properties, additionalProperties, items, uniqueItems, min/maxLength) with path-prefixed messages; collect() parses JSON editors first ('invalid JSON: ...'), flags missing required values, validates each field; errors shown on their field (invalid + errorMessage) and the run event only fires when valid. The run panel calls startRun then follows streamRun (live view); last inputs per system in sessionStorage (try/catch) pre-fill the form; 'Run again' re-runs them in one click, 'Edit inputs' returns to the form. Verified in headless Chromium on the sample app in dev mode (scratchpad scripts run.mjs, widgets.mjs, parallel.mjs), light and dark, no page errors. Checks: invalid JSON and wrong type flagged on the phrases editor, empty required text/integer/number/select flagged, nested 'age: must be an integer', 'items must be unique', 'x: must be a number'; runs start and the panel switches to the live view; re-run without dialog; inputs kept after a page reload.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Run inputs are validated against the schema with errors per field, a valid submit starts the run and switches to its live view, and the last inputs per system are kept for the session with a one-click re-run. Verified in headless Chromium.
<!-- SECTION:FINAL_SUMMARY:END -->
