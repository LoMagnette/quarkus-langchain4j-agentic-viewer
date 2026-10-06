---
id: TASK-46
title: >-
  T4.1: Detect langchain4j-agentic-patterns at build time and register adapters
  conditionally
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
updated_date: '2026-10-06 09:24'
labels:
  - deployment
  - patterns
milestone: m-4
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 73000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.1 (F5). Covers the open question "Is there a dedicated Quarkus extension for the patterns module, or do apps add langchain4j-agentic-patterns directly?", which decides how detection works. Without the module, planner nodes must fall back to the generic view (F2.4) with no errors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The open question on how apps add the patterns module is answered and recorded
- [ ] #2 Pattern adapters are registered only when langchain4j-agentic-patterns classes are on the classpath at build time
- [ ] #3 An app without the module starts with no errors, warnings or extra beans
- [ ] #4 Pattern adapter classes are never loaded when the module is absent
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Answer to the spec open question (from TASK-8.4): there is no Quarkus extension for the patterns module in quarkus-langchain4j 1.14.1; apps add dev.langchain4j:langchain4j-agentic-patterns directly (1.20.2-beta30 matches langchain4j-agentic). Detection must therefore look for the library classes (e.g. dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner). The module now also contains BDI, Debate and Voting planners, which the spec does not cover.
<!-- SECTION:NOTES:END -->
