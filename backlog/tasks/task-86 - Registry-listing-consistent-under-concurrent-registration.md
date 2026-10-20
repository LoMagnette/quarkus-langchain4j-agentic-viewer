---
id: TASK-86
title: Registry listing consistent under concurrent registration
status: Done
assignee: []
created_date: '2026-10-20 15:39'
updated_date: '2026-10-20 16:00'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 142000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (medium): `AgenticSystemRegistry.systems()` iterates a snapshot of `order` and looks each id up in the live `systems` map; `unregister()` removes from `systems` before `order`, so a concurrent listing can contain null (the UI then reads `system.name`), and a concurrent `register()` is visible in one structure before the other. Filtering nulls would only mask it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Listing, finding and the build-time listing read one consistent snapshot of the registered systems: never a null entry, never an id without its system
- [x] #2 Registration order and replace-in-place semantics are unchanged
- [x] #3 A concurrency test registers and unregisters while listing and never sees an inconsistent list
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Hold the registered systems in one immutable insertion-ordered map snapshot (volatile), replaced under a lock by register/unregister; systems(), find() and buildTimeSystems() read the current snapshot. 2. Concurrency test in deployment tests (registry built directly) hammering register/unregister vs systems(). 3. Builds.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The registry holds one immutable insertion-ordered map (volatile), replaced under a lock by register/unregister (re-registering the same instance is a no-op); systems(), find() and buildTimeSystems() read one snapshot. Contributions are cached with the system instance they were computed for, so a replaced system gets new ones. RegistryConsistencyTest: aListingNeverSeesAHalfRegisteredSystem fails on the old registry (null entries), passes now; aReplacedSystemKeepsItsPlace. Validation: mvn -o clean install 191/106/6 (1 skipped); mvn -o clean install -Ppatterns 200 (1 skipped)/144/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Listing and lookups read one immutable snapshot of the registered systems, replaced as a whole on registration, so a listing never has a null entry or an id without its system; order and replace-in-place unchanged. Verified with a concurrency test that fails on the old code and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
