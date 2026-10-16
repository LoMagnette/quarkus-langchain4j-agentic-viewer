---
id: TASK-35.1
title: 'T2.7a: Run form widgets generated from the JSON schema'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:33'
labels:
  - dev-ui
  - run
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-35
priority: high
type: feature
ordinal: 53000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.7 (F3.1, F3.2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The form is generated from getInputSchema for the selected system
- [x] #2 Strings, numbers, booleans and enums get text, number, checkbox and dropdown inputs
- [x] #3 Records, lists and maps get a JSON editor pre-filled with a skeleton from the schema
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. dev-ui/qwc-agentic-run-form.js (Lit): property schema (getInputSchema object schema) and values; one field per property in schema order: string -> vaadin-text-field, integer/number -> vaadin-integer-field / vaadin-number-field, boolean -> vaadin-checkbox, enum -> vaadin-select, anything else (records, arrays, maps, any) -> vaadin-text-area JSON editor pre-filled with a skeleton generated from the schema; required fields marked; the Java type is not in the object schema so the label shows the name and the schema kind.
2. The systems page loads getInputSchema when the run panel opens.
3. Browser check on the samples (string, list editor) and on a synthetic schema with every widget kind.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
dev-ui/qwc-agentic-run-form.js: fields generated from the getInputSchema object schema (loaded by the run panel), in schema order: string -> vaadin-text-field, integer -> vaadin-integer-field, number -> vaadin-number-field, boolean -> vaadin-checkbox, enum -> vaadin-select (an empty first option, 'Choose…' when required; items computed once per schema: a new items array on each render made vaadin-select throw in __updateValueButton), anything else (records, arrays, maps, any) -> monospace vaadin-text-area pre-filled with a skeleton from the schema (exported skeleton(): object properties, one-item arrays, '', 0, false, first enum value, null). Required fields marked. Verified in headless Chromium on the sample app in dev mode (scratchpad scripts run.mjs, widgets.mjs, parallel.mjs), light and dark, no page errors. Samples: StoryCreator topic text field, PhraseTranslator list editor pre-filled with [""]; a synthetic schema mounted on the form shows text/integer/number/checkbox/select and JSON editors {"name":"","age":0}, [""], {}, null.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run form is generated from the system's input schema with text, number, checkbox and dropdown inputs, and JSON editors pre-filled with a skeleton for records, lists and maps. Verified in headless Chromium on the samples and on a schema using every widget kind.
<!-- SECTION:FINAL_SUMMARY:END -->
