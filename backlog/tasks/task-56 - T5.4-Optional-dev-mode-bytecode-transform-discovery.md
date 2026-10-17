---
id: TASK-56
title: 'T5.4: Optional dev-mode bytecode transform discovery'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 13:39'
labels:
  - deployment
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-11
  - TASK-53
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 89000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.4 (F1.3.2, the `transform` strategy). Opt-in fallback for builder systems that are not beans, until U2 is available. Becomes unnecessary once U2 lands; check U2 status before starting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 With builder-discovery=transform, a dev-mode transform registers every .build() result of an AgenticServices builder
- [x] #2 Non-bean builder samples are listed when enabled
- [x] #3 The transform is never applied in prod or when another strategy is selected
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. builder-discovery=transform (adds to cdi). Deployment (dev only): BuildCallTransformer rewrites the application classes calling an agentic build() (BytecodeTransformerBuildItem): DUP the result, BuiltSystems.register(result, "Class#method:line").
2. runtime-dev BuiltSystems: latest instance per call site, weakly held, never fails; BuilderSystems.built() registers new/rebuilt roots on each listing (skips nested systems and the CDI producers' own builds) and observes them.
3. Tests: transform dev-mode fixture (field, per-call), cdi (not listed), outside dev mode (no transformer, mutation-checked); samples in dev mode.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

U2 checked before starting: parked by the owner (drafts in doc-7), so the transform is the way to see non-bean systems.
Verification: BuilderTransformDevModeTest (field system archiver and per-call system listed, CDI ones once, nested ones not, a call-site system runs), BuilderDiscoveryDevModeTest with the same fixture in cdi mode (not listed), BuilderTransformOutsideDevModeTest (no BytecodeTransformerBuildItem for the app class in test mode; fails when IsDevelopment is removed from the step). Sample app in dev mode with transform: ExpertResource#ask:45 listed once after 3 requests, ResearchService#build:35 once used (new /builder/research endpoint), the expert router runs with its full event stream. Full builds: default deployment 125 + integration 92; -Ppatterns failed once on HotReloadDevModeTest thread timing (passes alone twice; grace raised from 5 s to 30 s).

Final full builds: default deployment 125 + integration 92, -Ppatterns 129 (1 skipped) + 111.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
builder-discovery=transform lists the systems the application builds that are no beans: in dev mode only, the application classes calling an agentic build() report each result with its call site; new or rebuilt roots are registered and observed on the next listing. Verified with transform, cdi and outside-dev-mode tests (mutation-checked) and the samples in dev mode.
<!-- SECTION:FINAL_SUMMARY:END -->
