---
id: TASK-28
title: 'T2.1: JSON-RPC service: listSystems, getTopology, getInputSchema'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 13:49'
labels:
  - runtime-dev
  - json-rpc
milestone: m-2
dependencies:
  - TASK-20
  - TASK-25
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.1. The Dev UI reaches everything through one JSON-RPC service backed by `AgenticSystemRegistry`, so builder systems discovered at runtime (Phase 5) show up the same way as declarative ones.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 AgenticSystemRegistry holds one entry per root system keyed by a stable id, filled from build-time topology
- [x] #2 listSystems returns summaries with name, origin, pattern kinds and input count
- [x] #3 getTopology(systemId) returns the full tree and getInputSchema(systemId) the JSON schema
- [x] #4 The list and graph pages load their data over JSON-RPC
- [x] #5 JSON-RPC tests cover the three methods, including an unknown systemId
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime-dev AgenticSystemRegistry (@ApplicationScoped, registered unremovable by a dev-only build step): systems by id in insertion order, filled from the recorded build-time systems at construction; register(system) for Phase 5 builder systems (same id replaces).
2. AgenticSystemSummary(id, name, origin, sourceRef, patternKinds, inputCount): pattern kinds are the non-agent kinds in tree order (as the list page did in JS).
3. JSON-RPC: listSystems(), getTopology(systemId) -> AgenticSystem, getInputSchema(systemId) -> one JSON object schema (properties from each SystemInput schema, required list); unknown id -> error naming the id. getSystemCount() goes (listSystems covers it).
4. Dev UI: the systems page loads listSystems on connect and getTopology on selection (loading and error states); the agenticSystems build-time data is dropped. The card keeps its build-time count label.
5. Tests: dev-mode JSON-RPC test for the three methods incl. an unknown id; the build-time data and recorder-route tests are replaced; browser check of the page.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
runtime-dev: AgenticSystemRegistry (@ApplicationScoped, AdditionalBeanBuildItem.unremovableOf in a dev-only step; ConcurrentHashMap by id + registration order; @PostConstruct loads AgenticViewerRecorder.getSystems(); register(system) replaces by id, for Phase 5). AgenticSystemSummary(id, name, origin, sourceRef, rootKind, patternKinds, inputCount); pattern kinds = non-agent kinds in tree order (moved from the page's JS). AgenticViewerJsonRpcService: listSystems(), getTopology(systemId) -> AgenticSystem, getInputSchema(systemId) -> object schema (properties parsed from each SystemInput schema with Jackson, required list); unknown id -> IllegalArgumentException 'Unknown agentic system: <id>' (a JSON-RPC error). getSystemCount() removed. Deployment: agenticSystems build-time data dropped; the card keeps the build-time count label. Page: listSystems on connect (loading, error, empty states), getTopology on selection (loading, error with message). Tests: AgenticSystemsDevModeTest (QuarkusDevModeTest + DevUIJsonRPCTest): listSystems summary, getTopology children ids, getInputSchema exact JSON, unknown id for both methods through a raw java.net.http websocket client (DevUIJsonRPCTest never returns an error answer, nor a null result: it retries until 'Too many recursions'), card label; the build-time-data and recorder-route tests are replaced. Browser (headless Chromium): list of 9 samples, graphs, details, data flow over JSON-RPC; empty state and getTopology error checked by rewriting JSON-RPC answers with Playwright routeWebSocket; no page errors. Validation: deployment 67 tests, integration-tests 31, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The Dev UI now reads the agentic systems over JSON-RPC from an AgenticSystemRegistry in runtime-dev, filled from the build-time topology: listSystems gives summaries, getTopology the tree and getInputSchema the run-form schema, with errors for unknown ids. Verified by dev-mode JSON-RPC tests (including unknown ids over a raw websocket) and a headless Chromium check of the page, its empty state and error display.
<!-- SECTION:FINAL_SUMMARY:END -->
