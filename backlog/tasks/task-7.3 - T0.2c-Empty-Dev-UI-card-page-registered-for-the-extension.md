---
id: TASK-7.3
title: 'T0.2c: Empty Dev UI card page registered for the extension'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 08:25'
labels:
  - dev-ui
milestone: m-0
dependencies:
  - TASK-7.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-7
priority: high
type: task
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.2. Proves the deployment wiring (CardPageBuildItem, a Lit page, a JSON-RPC service from runtime-dev) end to end before real features land.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The Dev UI shows the extension card with one page in dev mode of the sample app
- [x] #2 The page calls a trivial JSON-RPC method served from runtime-dev and displays its result
- [x] #3 No card or JSON-RPC service is registered outside dev mode
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Register AgenticViewerJsonRpcService (now in runtime-dev) through a dev-only JsonRPCProvidersBuildItem build step.
2. Add a skeleton "Agentic Systems" page (qwc-agentic-systems.js, Lit + JsonRpc) to the existing card. It calls getAgentCount over JSON-RPC and displays the result; T1.5 grows it into the system list. The prototype Mermaid page stays until TASK-26.
3. Deployment tests:
   - QuarkusDevModeTest + DevUIJsonRPCTest (io.quarkus:quarkus-devui-test-spi) on a fixture app with real declarative agents and a builder sequence: JSON-RPC answers from runtime-dev and the prototype graph finds the real-annotation agents (closes the open checks of TASK-7.1 and TASK-7.2).
   - QuarkusUnitTest in test (non-dev) launch mode: no JSON-RPC service bean is registered.
4. Manual check: run the sample app in dev mode and load the Dev UI card and page.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validation:
- mvn clean install: 9 tests pass (deployment 3, integration-tests 6).
- AgenticViewerJsonRpcDevModeTest (QuarkusDevModeTest + DevUIJsonRPCTest): getAgentCount and getGraph answer from runtime-dev in dev mode; the graph has the fixture agents, no dev.langchain4j.* agent, and builder usage only in the fixture builder class.
- AgenticViewerOutsideDevModeTest: a build step in test launch mode sees neither our JSON-RPC provider nor our card. Mutation-checked: removing either onlyIf = IsDevelopment.class makes it fail. (A first version compared static fields and Class objects across class loaders and passed vacuously; it now reports through a system property and compares class names.)
- Sample app in dev mode, checked with headless Chromium (Playwright): the card shows the "Agentic Diagram" and "Agentic Systems" pages; the Agentic Systems page renders "13 agents discovered" from JSON-RPC; the prototype diagram page still renders (13 nodes); no browser errors.

AC 1 deviation: the card has two pages, not one. The prototype Mermaid page stays until TASK-26 retires it.

JSON-RPC method names use an underscore separator in Quarkus 3.33: <namespace>_<method>, namespace quarkus-langchain4j-agentic-viewer.

Regression found and fixed here (introduced by TASK-7.2): with the real library on the index, both prototype scanners also picked up langchain4j internals (6 extra agents such as dev.langchain4j.agentic.supervisor.SupervisorAgent, plus imperative locations in AgenticServices, AgentUtil, AgenticRecorder$1). DeclarativeAgentScanner now skips annotations declared in dev.langchain4j.* (same rule as quarkus-langchain4j-agentic validation); the processor only passes the root archive and non-langchain4j archives to ImperativeAgentScanner. The sample app now shows 12 scanned agents and only MixedPatternExample as builder usage.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Registered the runtime-dev AgenticViewerJsonRpcService through a dev-only JsonRPCProvidersBuildItem and added a skeleton "Agentic Systems" Dev UI page (qwc-agentic-systems.js) that shows the agent count from JSON-RPC. Added deployment tests for dev mode (JSON-RPC over the Dev UI websocket) and non-dev mode (no card or service registered, mutation-checked). Also fixed a regression from TASK-7.2 where the prototype scanners listed langchain4j library internals. Verified with mvn clean install (9 tests) and a headless-browser check of the sample app in dev mode.
<!-- SECTION:FINAL_SUMMARY:END -->
