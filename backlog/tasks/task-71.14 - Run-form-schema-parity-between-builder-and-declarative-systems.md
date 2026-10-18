---
id: TASK-71.14
title: Run form schema parity between builder and declarative systems
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 21:10'
labels:
  - code-review
  - builder
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RuntimeInputSchema (builder systems) decides required fields from declaration annotations only, while the build-time InputSchemaGenerator also looks at type-use annotations and parameter annotations. A jspecify @Nullable (type-use) or a parameter @Nullable on a typed builder interface makes a field required in a builder form but optional in a declarative one. The two generators duplicate their type tables, which is how they drifted.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Type-use and parameter @Nullable make a field optional in builder forms as in declarative ones
- [x] #2 RuntimeInputSchemaTest covers both annotations for parity
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RuntimeInputSchema.required also reads the type-use annotations of record components and parameters (AnnotatedType).
2. InputSchemaGenerator.required: a record component's type-use annotations are on its field and accessor in Jandex: read them too.
3. BuilderTopology: the parameters of a typed builder interface become optional AgentArguments when @Nullable or Optional.
4. NullableParityTest (declaration and type-use @Nullable on record components, typed builder interface parameters).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding: the build-time generator had the same gap for type-use @Nullable on record components (Jandex keeps those annotations on the field/accessor type, not on the component's type): fixed too, so declarative and builder forms agree. NullableParityTest failed before on both points and passes. Class-retention @Nullable (JetBrains) remains build-time only (not visible to reflection). Both CI configurations: BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
@Nullable makes an input optional in builder forms as in declarative ones: type-use annotations (jspecify) are read by both generators (the build-time one missed them on record components too), and the parameters of a typed builder interface carry their nullability. Verified with NullableParityTest (fails before) and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
