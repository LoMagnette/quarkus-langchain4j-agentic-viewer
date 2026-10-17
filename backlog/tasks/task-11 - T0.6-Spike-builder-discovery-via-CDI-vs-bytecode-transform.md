---
id: TASK-11
title: 'T0.6: Spike - builder discovery via CDI vs bytecode transform'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-17 06:02'
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
- [x] #1 A written finding compares coverage of the cdi and transform strategies against the builder samples (bean, non-bean, per-request)
- [x] #2 The finding estimates implementation and maintenance cost of each and recommends a default
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Prototype BuilderDiscoverySpikeTest: CDI candidates by bean type without creation; build() call sites in the application bytecode (what a transform would wrap), against the TASK-66 builder samples.
2. Finding (doc-6): coverage table, implications, cost and maintenance, recommended default.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Finding in Backlog doc-6. CDI by type finds only the UntypedAgent bean (typed interface beans tell nothing before creation; field and per-request systems are no beans); a build-time scan of producer bodies for agentic build() calls adds the typed beans. The transform sees all four samples' call sites (6 build() calls, nested loop included), but only once built and one instance per request. Recommendation: default cdi with the producer scan and lazy creation; transform opt-in until U2 (spi).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Compared CDI and bytecode-transform builder discovery on the builder samples with a prototype (BuilderDiscoverySpikeTest, 2 tests): CDI by type finds produced UntypedAgent beans only, plus typed beans with a build-time producer scan; the transform covers every build() call site including non-bean and per-request systems. doc-6 estimates cost (CDI small, transform moderate with dedup/roots/hot reload) and recommends cdi (with producer scan) as default, transform opt-in until U2.
<!-- SECTION:FINAL_SUMMARY:END -->
