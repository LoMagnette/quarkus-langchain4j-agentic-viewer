---
id: TASK-51
title: 'T4.6: Test matrix with and without the patterns module'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
updated_date: '2026-10-06 09:28'
labels:
  - testing
  - ci
  - patterns
milestone: m-4
dependencies:
  - TASK-48
  - TASK-49
  - TASK-50
documentation:
  - spec-breakdown.md
priority: medium
type: task
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.6. The patterns module is optional; regressions in either configuration must be caught.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CI runs the test suite with and without langchain4j-agentic-patterns
- [ ] #2 Deployment tests cover build-time scanning in both configurations
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
From TASK-8.4: the patterns samples are behind the integration-tests Maven profile "patterns" (src/patterns/java, src/patterns-test/java). Each matrix leg must be a separate clean build: switching the profile without clean reuses a stale Quarkus application model in target/ and fails with LinkageError/ClassCastException (patterns classes loaded outside the Quarkus class loader).
<!-- SECTION:NOTES:END -->
