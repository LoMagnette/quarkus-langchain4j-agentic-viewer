---
id: TASK-57.1
title: 'T5.5a: Deduplicate repeatedly built systems by call site and structure'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 13:39'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-57
priority: low
type: feature
ordinal: 91000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T5.5 (F1.4). Systems built more than once (e.g. per request) would otherwise flood the list.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Systems built several times from the same call site with the same structure map to one registry entry
- [x] #2 Runs of any instance are attributed to that entry
- [x] #3 The per-request builder sample appears once after several requests
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. One entry per call site and shape: BuiltSystems keeps the latest instance per (call site, shape fingerprint of kinds/names/types), shapes capped at 10 per call site; entry ids callSite, callSite~2...
2. Runs started from the Dev UI invoke the latest instance of the entry.
3. Tests: fixture call site building two shapes; per-call system listed once; sample router once after several requests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Verification: BuilderTransformDevModeTest: perCall (built 3 times) one entry; variable (one build() call, two shapes) two entries variable and variable~2; run of the per-call entry succeeds. Sample app (transform): the per-request ExpertResource router listed once after 3 requests. Runs of an entry use its latest instance (BuilderSystems.root); only Dev UI runs are recorded, as for every system.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Systems built several times map to one entry per call site and shape (latest instance, at most 10 shapes per call site); runs use the latest instance. Verified by the transform dev-mode test (per-call system once, two shapes at one call site as two entries) and the per-request sample listed once after three requests.
<!-- SECTION:FINAL_SUMMARY:END -->
