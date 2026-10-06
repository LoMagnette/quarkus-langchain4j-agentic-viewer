---
id: TASK-30
title: 'T2.3: Deserialize form JSON to parameter types and invoke the CDI bean'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies:
  - TASK-29.1
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.3. Typed declarative systems are CDI beans; a run calls the root method with arguments built from the form JSON.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Form JSON is converted to the root method parameter types (primitives, enums, records, lists, maps)
- [ ] #2 The root system CDI bean method is invoked with those arguments inside the run
- [ ] #3 A conversion error fails the run with a clear message naming the parameter
- [ ] #4 Every typed sample system runs from a unit test through RunManager
<!-- AC:END -->
