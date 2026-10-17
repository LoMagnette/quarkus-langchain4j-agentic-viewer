---
id: TASK-58
title: 'T5.6: Run form and invocation for UntypedAgent'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 06:31'
labels:
  - runtime-dev
  - dev-ui
  - builder
  - run
  - deferred
milestone: m-5
dependencies:
  - TASK-53
  - TASK-35
  - TASK-30
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 93000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.6 (F3.1). Untyped systems have no method signature, so the form is built from the required input keys and the run invokes the agent with a map.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 getInputSchema returns a schema built from the required input keys of an UntypedAgent
- [x] #2 The run invokes the UntypedAgent with a map of the entered values
- [x] #3 The untyped sample runs from the UI
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Schema: RuntimeInputSchema (reflection, same rules as the build-time InputSchemaGenerator) for builder SystemInputs; typed builder interfaces take their inputs from their one method.
2. Run: BuilderSystems keeps root agents; CdiRunInvoker invokes UntypedAgent with converted values, typed interfaces through their method; RunManager gets the lookup. Re-enable Run in the UI and JSON-RPC.
3. Tests: schema parity (deployment), dev-mode fixture runs, integration sample runs; browser run of the builder sample.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Verification: RuntimeInputSchemaTest (parity on 20 types incl. records, enums, Optional, @Nullable, collections, maps, arrays, recursion) 2/2; BuilderDiscoveryDevModeTest (Greeter schema from its method; pipeline and greeter runs SUCCEEDED) 2/2; BuilderRunsTest 2/2 (UntypedAgent with map -> 'A story about a lighthouse (draft 4)'; typed BuilderStoryWriter through its method); BuilderTopologyTest: the builder twin's inputs equal the declarative StoryCreator's. Dev UI (scratch dev mode 8091, headless Chromium): builderStoryCreator run succeeded with that output, no page error. No live highlighting yet (listener not attached to builder systems: T5.3).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Builder systems now run from the Dev UI: their form schema is generated at runtime with the build-time rules (parity-tested), an UntypedAgent is invoked with a map of converted values and a typed builder interface through its method. Verified with schema, dev-mode and integration tests and a browser run of the builder StoryCreator sample.
<!-- SECTION:FINAL_SUMMARY:END -->
