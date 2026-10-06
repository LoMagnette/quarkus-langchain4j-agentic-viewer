---
id: TASK-65
title: 'T6.5: Compatibility CI against langchain4j-agentic snapshots'
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
labels:
  - ci
  - testing
milestone: m-6
dependencies:
  - TASK-51
documentation:
  - spec-breakdown.md
priority: low
type: task
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.5. langchain4j-agentic and the patterns module are experimental and change often; a nightly job against snapshots catches breakage early.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A nightly CI job builds and tests against langchain4j-agentic and patterns snapshots
- [ ] #2 The job is green
<!-- AC:END -->
