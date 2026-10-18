---
id: TASK-71.9
title: Dev UI run form loses user edits and sends unwanted optional values
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:17'
labels:
  - code-review
  - dev-ui
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: bug
ordinal: 115000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
qwc-agentic-run-panel passes session.get(LAST_INPUTS...) as .values on every render; session.get parses JSON each time so the form sees a new values object and resets all fields (qwc-agentic-run-form updated()). Any re-render, e.g. the GOAP preview response 250 ms after an edit, throws away what the user typed. Optional JSON inputs are pre-filled with a skeleton and sent unless cleared (an optional List<String> receives [""]), and an optional boolean is always sent as false, which also counts as a provided key in the GOAP preview.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Editing a field and receiving a goal path preview keeps the edit
- [x] #2 The form is pre-filled from the last inputs only when the system changes or the user asks to reuse them
- [x] #3 An optional input the user did not fill is not sent
- [x] #4 A browser test covers the edit plus preview case
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run panel parses the last inputs once (system change) and keeps them, updated when a run is requested: stable values for the form.
2. Run form resets its fields only when the schema changes or the values' content changes.
3. Optional JSON inputs start empty with the skeleton as placeholder; an optional boolean has no value until the user sets it (initial checked-changed ignored).
4. Browser tests: edit + panel re-render keeps the edit; optional inputs left empty are not sent (new OptionsWorkflow fixture).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Both new browser tests failed before (topic reset to the last run's value; tags pre-filled with the skeleton, urgent sent as false). The re-render is triggered in the test by a reactive update of the panel (_history reassigned): the same path as a goal path preview or a history refresh, which the fixture app (no GOAP planner) cannot produce on demand. systemList now counts two declarative systems (the new fixture). ui-tests profile: 9 tests pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run form keeps what the user typed when the panel renders again (the last inputs are parsed once and the form only resets on new content), and optional inputs the user left empty (JSON skeletons, never-set booleans) are no longer sent. Verified with two new browser tests that fail before, and the full ui-tests profile.
<!-- SECTION:FINAL_SUMMARY:END -->
