---
id: TASK-30
title: 'T2.3: Deserialize form JSON to parameter types and invoke the CDI bean'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 13:59'
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
- [x] #1 Form JSON is converted to the root method parameter types (primitives, enums, records, lists, maps)
- [x] #2 The root system CDI bean method is invoked with those arguments inside the run
- [x] #3 A conversion error fails the run with a clear message naming the parameter
- [x] #4 Every typed sample system runs from a unit test through RunManager
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CdiRunInvoker (RunInvoker): loads the root class with the run thread's class loader, picks the root method (sourceRef method name, agentic annotation), gets the bean from ArC and invokes the method; InvocationTargetException is unwrapped.
2. Arguments from the form JSON with Jackson (findAndRegisterModules) converted to each parameter's generic type: primitives, enums, records, lists, maps; key = @V value or parameter name; @MemoryId gets the given value or a fresh UUID; a missing primitive or a conversion failure throws an IllegalArgumentException naming the parameter, which fails the run with that message.
3. Tests: unit tests of the argument conversion (every type, missing and wrong values); integration-tests @QuarkusTest running every typed sample through a RunManager built on the scanned sample systems and CdiRunInvoker (runtime-dev beans only exist in dev mode), same inputs and outputs as DeclarativeSamplesTest.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CdiRunInvoker: root class loaded with the run thread's class loader, root method by sourceRef name (agentic-annotated first), bean from ArC, InvocationTargetException unwrapped. Arguments (public static arguments(Method, Map)): key = @V value or parameter name; Jackson convertValue (findAndRegisterModules) to the parameter's generic type: primitives, enums, records, lists, sets, maps, Optional, LocalDate; @MemoryId gets its value or a fresh UUID; AgenticScope left to langchain4j; missing object values are null (an @ErrorHandler may supply a default, e.g. ResilientStoryWriter), a missing primitive -> "Missing value for input 'x'", a failed conversion -> "Cannot convert input 'x' to <type>: ...", which fails the run with that message. Finding: each invocation now runs in its own CDI request context. Without it, the reused run threads had no request context and the request-scoped chat memory of the agents leaked between runs: after the spike tests, DraftReviewer's drafter got BankSupervisor messages and the scripted model answered with a 'credit' tool call (order-dependent failure). Tests: RunArgumentsTest (5), integration-tests SampleRunsTest (11: the 9 samples with DeclarativeSamplesTest inputs/outputs, a run with a missing input, a conversion error naming the input, bank then draft three times in a row), patterns profile PatternSampleRunsTest (3: GOAP, P2P, blackboard). Runs are driven by a RunManager built in the test because runtime-dev beans only exist in dev mode.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Runs now call the root CDI bean of a declarative system with arguments converted from the form JSON (primitives, enums, records, lists, maps), fail with a message naming the input on bad values, and execute in their own request context so chat memory does not leak between runs. Verified by conversion unit tests and by running every typed sample, including the patterns samples, through RunManager.
<!-- SECTION:FINAL_SUMMARY:END -->
