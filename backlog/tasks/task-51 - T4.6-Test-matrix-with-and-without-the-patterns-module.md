---
id: TASK-51
title: 'T4.6: Test matrix with and without the patterns module'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:42'
labels:
  - testing
  - ci
  - patterns
milestone: m-4
dependencies:
  - TASK-48
  - TASK-49
  - TASK-50
documentation:
  - spec-breakdown.md
priority: medium
type: task
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.6. The patterns module is optional; regressions in either configuration must be caught.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 CI runs the test suite with and without langchain4j-agentic-patterns
- [x] #2 Deployment tests cover build-time scanning in both configurations
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. deployment: profile 'patterns' (like integration-tests) adding langchain4j-agentic-patterns as a test dependency and src/patterns-test/java as test sources: build-time detection with the module (PlannerTypeDetector on fixtures constructing the three pattern planners; adapter selection with the real class path) and a dev-mode test whose ArC bean list has the three adapters (mirror of the existing no-module test).
2. .github/workflows/build.yml: matrix over the two configurations (default, -Ppatterns), JDK 25, mvn clean install (all modules, tests included).
3. Verify locally with the exact matrix commands (no remote to run the workflow on).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
From TASK-8.4: the patterns samples are behind the integration-tests Maven profile "patterns" (src/patterns/java, src/patterns-test/java). Each matrix leg must be a separate clean build: switching the profile without clean reuses a stale Quarkus application model in target/ and fails with LinkageError/ClassCastException (patterns classes loaded outside the Quarkus class loader).

CI: .github/workflows/build.yml, matrix configuration without-patterns / with-patterns (mvn -B clean install [-Ppatterns], JDK 21, Maven cache). Not executed on GitHub (no git remote in this repository); both commands run locally: without the module deployment 110 + integration-tests 69, with -Ppatterns deployment 113 (1 skipped: the no-module dev-mode test, assumption on the module's presence) + integration-tests 88, all passing. Deployment build-time coverage with the module: PatternDetectionTest (GOAP/P2P/BLACKBOARD tagging from @PlannerSupplier bytecode) and PatternAdaptersDevModeTest (three adapter beans, getTopology tag, previewGoalPath); without: PatternAdaptersSelectionTest, noPatternAdapterWithoutTheModule.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CI builds and tests everything with and without langchain4j-agentic-patterns (GitHub Actions matrix), and the deployment module tests build-time scanning and adapter registration in both configurations. Verified by running both matrix commands locally; the workflow itself has not run on GitHub yet.
<!-- SECTION:FINAL_SUMMARY:END -->
