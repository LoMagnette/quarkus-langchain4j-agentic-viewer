---
id: TASK-71.8
title: Runtime artifact brings quarkus-vertx-http into prod applications
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:13'
labels:
  - code-review
  - build
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: bug
ordinal: 114000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
runtime/pom.xml depends on quarkus-vertx-http at compile scope although neither runtime nor runtime-dev uses Vert.x; quarkus-langchain4j-core only brings quarkus-vertx. A batch, command-mode or messaging application adding the extension starts an HTTP server in prod. ProdArtifactIT does not catch it as it only looks for Dev UI code.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The runtime artifact adds no HTTP server to an application that has none
- [x] #2 The Dev UI page still works in dev mode
- [x] #3 A test or build check fails if the runtime artifact depends on quarkus-vertx-http again
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Remove quarkus-vertx-http from runtime/pom.xml; deployment depends on quarkus-devui-deployment-spi (the only Dev UI API it uses) instead of quarkus-vertx-http-deployment.
2. Deployment tests: quarkus-vertx-http-deployment test-scoped (dev-mode fixtures need HTTP for the Dev UI).
3. Enforcer bannedDependencies on the runtime module (direct and transitive quarkus-vertx-http).
4. Full builds and the browser tests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Evidence: mvn dependency:tree -pl runtime -Dincludes=io.quarkus:quarkus-vertx-http listed it only as a direct dependency before; now the enforcer rule (searches transitively) passes, and re-adding the dependency fails validate with the rule's message (checked). Dev UI: dev-mode tests failed without HTTP in their fixtures (connection refused) until the test-scoped vertx-http-deployment was added; both CI configurations and the ui-tests profile (AgenticSystemsPageUiTest 6, LargeSystemUiTest 1) pass. Applications using the Dev UI have HTTP themselves.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The runtime artifact no longer brings quarkus-vertx-http (an HTTP server) into prod applications: the deployment uses only the Dev UI deployment SPI, and an enforcer rule fails the build if the runtime module depends on quarkus-vertx-http again, directly or transitively. Verified with the enforcer check (fails when re-added), both CI configurations and the browser tests.
<!-- SECTION:FINAL_SUMMARY:END -->
