---
id: TASK-12
title: 'T0.7: Spike - identify and wrap a Supplier<Planner>'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 09:41'
labels:
  - spike
  - runtime-dev
  - planner
milestone: m-0
dependencies:
  - TASK-8
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.7. Also answers the open question "Can a listener or planner wrapper be added after .build(), or only before?".

Planner decisions are only observable by decorating the planner supplier (until U3). In the declarative-first scope (decision-2) planners come from declarative `@PlannerSupplier` methods; wrapping `plannerBuilder` planners is deferred to T5.3.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A decorated planner emits each Action returned by firstAction/nextAction in the custom-planner sample
- [x] #2 The finding states whether wrapping is possible after .build() or only before
- [x] #3 The finding covers declarative @PlannerSupplier planners
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read how planner-based agents hold their planner (PlannerBasedInvocationHandler, PlannerBasedServiceImpl, Quarkus agent beans).
2. Prototype a delegating DecisionRecordingPlanner and install it on root agents after build (reflection), for the custom planner sample, the supervisor and a sequence.
3. Write the finding: before/after build, alternatives, recommendation for T3.2.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding in Backlog doc-4. Prototype PlannerWrapSpikeTest (3 tests): the decorated planner records every Action of DraftReviewer's custom planner (writeDraft, review, writeDraft, review, done), of the supervisor (exchange, withdraw, credit, done with result) and of a sequence. No public pre-build hook exists in the declarative path; after build the supplier is a private final field, replaceable by reflection (works on Java 25 without warning). Under Quarkus the root bean's agent field is the PlannerBasedInvocationHandler itself, not a JDK proxy. Nested planner agents (walk of subagents) not verified. integration-tests: 30 tests pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Answered T0.7: planner decisions can be captured by wrapping the planner supplier of each root agent after build (reflection on a private final field; no pre-build hook exists for declarative planner agents). A delegating decorator records every Action for custom, supervisor and sequence planners (PlannerWrapSpikeTest). Alternatives and the recommendation for T3.2 are in doc-4.
<!-- SECTION:FINAL_SUMMARY:END -->
