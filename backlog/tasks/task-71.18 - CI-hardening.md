---
id: TASK-71.18
title: CI hardening
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
  - ci
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: chore
ordinal: 124000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
build.yml and snapshots.yml: no native job so NativeArtifactIT always skips; the snapshot profile hard-codes langchain4j 1.23.0-SNAPSHOT which goes stale after the release; snapshots only run with patterns; exec:java uses an undeclared, unpinned exec-maven-plugin; Chromium is not cached; no timeout-minutes (a hung dev-mode or Playwright test runs 6 h); no test report upload on failure; actions pinned to tags; the generated config reference in docs is not checked for drift; nothing enforces that langchain4j.version matches what quarkus-langchain4j brings.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A CI job (scheduled or on main) runs the native build and NativeArtifactIT
- [x] #2 The snapshot workflow resolves the current langchain4j snapshot without a hard-coded version
- [x] #3 Every job has a timeout and uploads surefire/failsafe reports on failure
- [x] #4 Plugins used by CI are declared with versions in the poms
- [x] #5 CI fails when the generated config reference differs from the committed one
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. build.yml: timeout-minutes, test reports uploaded on failure, generated docs drift check (git diff), Chromium cache.
2. native.yml: on main, weekly and on demand: native container build of the sample app, NativeArtifactIT and ProdStartupIT.
3. snapshots.yml: newest langchain4j BOM snapshot resolved from the snapshot repository metadata, both configurations, timeout, reports.
4. exec-maven-plugin declared with its version (root pluginManagement).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified locally: the snapshot resolution command returns 1.23.0-SNAPSHOT; the native command of native.yml passes (container build, NativeArtifactIT, ProdStartupIT startup); a docs build rewrites a stale includes file (so a stale committed reference shows as a git diff in CI); exec plugin version 3.6.4 in the effective pom of deployment -Pui-tests. Not done: actions pinned to commit SHAs (not an AC); the workflows themselves run on GitHub only after a push.

Final verification: mvn -o clean install three rounds in a row in both configurations, all green (deployment 151/155, integration 106/127, failsafe 6 incl. ProdStartupIT); ui-tests 22/22; native container build with NativeArtifactIT and ProdStartupIT green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CI hardened: every job has a timeout and uploads test reports on failure, the build checks the generated docs are committed, Chromium is cached, a native workflow (main, weekly, on demand) runs the native build with NativeArtifactIT and ProdStartupIT, the nightly snapshot job resolves the newest langchain4j snapshot and runs both configurations, and the exec plugin is declared with its version. Commands verified locally.
<!-- SECTION:FINAL_SUMMARY:END -->
