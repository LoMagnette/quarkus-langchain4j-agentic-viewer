---
id: TASK-71.7
title: 'Builder systems: robustness of discovery and registration'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:01'
labels:
  - code-review
  - builder
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: bug
ordinal: 113000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
AgenticSystemRegistry calls BuilderSystems.built() on every find()/systems(); built() has no guard around BuilderTopology.system, so one system whose topology throws makes every JSON-RPC call fail, declarative systems included; discover() only guards bean creation. builderSystemsLoaded is set before discover() runs (concurrent calls see builder systems as unknown, a failing discover is never retried). BuiltSystems is static and only cleared by the @PreDestroy of the lazily created BuilderSystems bean, so a hot reload before the Dev UI is opened keeps old entries and pins the old application class loader. AgenticViewerRecorder.builderCandidates is static and not reset when builder-discovery becomes none. In transform mode the dedupe against CDI producers misses producer fields and build() calls in lambdas or helpers, listing the system twice. An inner system registered as root before the outer build() adopted it stays listed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A builder system whose topology cannot be built is logged and skipped; other systems keep working over JSON-RPC
- [x] #2 A failing bean or topology in discovery does not stop the other candidates
- [x] #3 Builder systems are never reported unknown while the first discovery runs
- [x] #4 After a hot reload no builder system or candidate of the previous application is listed
- [x] #5 A builder system produced by a producer field is listed once in transform mode
- [x] #6 Tests cover the cases above
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. BuilderSystems: guard each topology build in discover() and built(); remember a failing instance per entry so it is not retried until rebuilt.
2. Producer dedupe in transform mode: identity with CDI roots, plus call sites in a producer lambda or in the constructor/static init setting a producer field.
3. BuilderSystems.stale(): systems shown before that got a parent since; the registry unregisters them.
4. Registry: the loaded flag is set after a successful discovery (concurrent callers wait on the lock); a failing discovery is logged and retried.
5. Recorder.setSystems (static init of each start) clears BuiltSystems and the candidates; the processor records the candidates in the same step, after it (empty with builder-discovery=none), instead of a separate unordered step; MIXED only computed when discovery is on.
6. Tests: BuilderSystemsRobustnessTest, RegistryBuilderLoadingTest (runtime-dev packages), FieldProducer fixture in BuilderTransformDevModeTest.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
BuilderTransformDevModeTest lists the producer field twice with the old producer rule (checked by reverting just that rule) and once now. Hot reload: covered by the recorder unit test (the next app's static init clears built systems and candidates) rather than a dev-mode reload test. Also done here because it is the same build step: with builder-discovery=none no system is tagged MIXED (an AC of TASK-71.13). Both CI configurations: BUILD SUCCESS (deployment 138/142, integration 106/125).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Builder discovery no longer breaks or goes stale: a system that cannot be shown is logged and skipped (others keep working), concurrent callers wait for the first discovery and a failed one is retried, each application start forgets the previous one's built systems and candidates, a producer field (or a build in a producer lambda) is listed once in transform mode, and a system adopted by another after being listed is removed. Verified with new unit tests, the extended transform dev-mode test and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
