---
id: TASK-7.1
title: 'T0.2a: Add dev-only runtime-dev module'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 07:58'
labels:
  - build
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-7
priority: high
type: task
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.2. All live behaviour (registry, listener, RunManager, JSON-RPC) goes into `runtime-dev`, which must only be present in dev mode so prod and native builds carry no Dev UI code (F6.4).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A runtime-dev module exists and is declared as a dev-mode-only (conditional dev) dependency of the runtime extension
- [x] #2 A prod build of the sample app does not contain any runtime-dev class
- [x] #3 runtime contains only model records and SPI interfaces, no CDI beans or behaviour
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Follows the Quarkus core pattern (quarkus-arc / quarkus-arc-dev), checked in the 3.30.8 artifacts:
1. New module runtime-dev (artifactId quarkus-langchain4j-agentic-viewer-dev): a plain jar depending on runtime.
2. Declare it in runtime via quarkus-extension-maven-plugin <conditionalDevDependencies>, so it is only on the classpath in dev mode.
3. Move behaviour out of runtime: AgenticViewerRecorder and the (currently unregistered) AgenticViewerJsonRpcService go to runtime-dev (package ...viewer.runtime.dev). The recordGraph build step becomes dev-only, since the recorder no longer exists in prod.
4. deployment depends on runtime-dev, like quarkus-arc-deployment depends on quarkus-arc-dev.
5. Verify: the built runtime jar declares conditional-dev-dependencies; a prod package of integration-tests has no -dev jar; runtime holds only model records; existing tests pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validation (mvn clean install, all 6 tests pass):
- runtime jar META-INF/quarkus-extension.properties contains conditional-dev-dependencies=io.quarkiverse.langchain4j:quarkus-langchain4j-agentic-viewer-dev::jar:0.0.1-SNAPSHOT.
- Prod package integration-tests/target/quarkus-app: only the runtime jar of the extension in lib/main; no viewer/runtime/dev/ class in lib, app or generated-bytecode jars.
- runtime jar classes: only model records and enums under runtime/model.
Not yet proven: runtime-dev being loaded in dev mode; covered by the QuarkusDevModeTest planned in TASK-7.3.
Note: recordGraph build step is now dev-only because AgenticViewerRecorder moved to runtime-dev.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added the dev-only runtime-dev module (quarkus-langchain4j-agentic-viewer-dev), declared via conditionalDevDependencies in the runtime extension descriptor, following the quarkus-arc / quarkus-arc-dev pattern. Moved AgenticViewerRecorder and AgenticViewerJsonRpcService into runtime-dev (package ...viewer.runtime.dev); deployment depends on runtime-dev and the recordGraph step is dev-only. Verified with mvn clean install (6 tests pass), the runtime extension properties, and inspection of the prod quarkus-app (no runtime-dev classes).
<!-- SECTION:FINAL_SUMMARY:END -->
