---
id: TASK-58
title: 'T5.6: Run form and invocation for UntypedAgent'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
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
- [ ] #1 getInputSchema returns a schema built from the required input keys of an UntypedAgent
- [ ] #2 The run invokes the UntypedAgent with a map of the entered values
- [ ] #3 The untyped sample runs from the UI
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
