---
id: TASK-55
title: Builder discovery strategy spi using the U2 observer
status: To Do
assignee: []
created_date: '2026-10-06 06:16'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-2
  - TASK-53
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 88000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec F1.3.3: the `spi` value of `quarkus.langchain4j.agentic.dev-ui.builder-discovery`, using the upstream registration hook once U2 exists. It finds every built root system, including non-bean ones, without a bytecode transform.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 With builder-discovery=spi, every root system built through AgenticServices is registered via the U2 observer
- [ ] #2 Non-bean and per-request builder samples are listed
- [ ] #3 If the upstream hook is missing at runtime, a clear warning is logged and cdi discovery is used
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
