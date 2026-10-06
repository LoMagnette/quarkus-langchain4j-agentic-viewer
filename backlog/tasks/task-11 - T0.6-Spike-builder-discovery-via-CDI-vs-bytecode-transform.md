---
id: TASK-11
title: 'T0.6: Spike - builder discovery via CDI vs bytecode transform'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 06:26'
labels:
  - spike
  - builder
  - deferred
milestone: m-5
dependencies:
  - TASK-10
documentation:
  - spec-breakdown.md
priority: low
type: spike
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.6. Builder systems can be discovered by inspecting CDI beans (`UntypedAgent` / `AgentInstance` types) or by a dev-mode transform registering every `.build()` result. The choice drives Phase 5 and the default of `quarkus.langchain4j.agentic.dev-ui.builder-discovery`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A written finding compares coverage of the cdi and transform strategies against the builder samples (bean, non-bean, per-request)
- [ ] #2 The finding estimates implementation and maintenance cost of each and recommends a default
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
