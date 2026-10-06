---
id: TASK-25
title: 'T1.9: Input JSON schema generation from root method parameters'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - run
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.9 (feeds F3.1 to F3.3). The run form is generated from a JSON schema of the root method parameters and validated against it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A JSON schema is generated for each typed root system from its method parameters and their names
- [ ] #2 Schemas cover primitives, strings, enums, records, lists and maps
- [ ] #3 Required parameters are marked required
- [ ] #4 Unit tests cover each supported type
<!-- AC:END -->
