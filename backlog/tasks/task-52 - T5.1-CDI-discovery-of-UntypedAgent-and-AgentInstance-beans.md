---
id: TASK-52
title: 'T5.1: CDI discovery of UntypedAgent and AgentInstance beans'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-11
  - TASK-28
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.1 (F1.3.1, the default `cdi` strategy of `quarkus.langchain4j.agentic.dev-ui.builder-discovery`). Builder systems only exist at runtime; the cheapest way to find them is to inspect CDI beans whose type is `UntypedAgent` or implements `AgentInstance`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 At startup in dev mode, beans of type UntypedAgent or implementing AgentInstance are registered in AgenticSystemRegistry with origin BUILDER
- [ ] #2 Produced builder systems from the samples appear in listSystems
- [ ] #3 Beans that are sub-agents of another discovered system are not listed as roots
- [ ] #4 Discovery does not eagerly create beans with side effects beyond what the app already does
- [ ] #5 quarkus.langchain4j.agentic.dev-ui.builder-discovery exists with cdi as default, documented and tested
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
