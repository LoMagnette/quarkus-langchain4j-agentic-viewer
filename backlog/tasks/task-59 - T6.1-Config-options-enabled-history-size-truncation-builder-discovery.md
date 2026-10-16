---
id: TASK-59
title: 'T6.1: Config options: enabled, history size, truncation'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-16 20:49'
labels:
  - config
  - runtime-dev
milestone: m-6
dependencies:
  - TASK-29.2
  - TASK-34
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 94000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.1 (F6.1, F6.3). Consolidates every option under `quarkus.langchain4j.agentic.dev-ui.*` with docs and tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 quarkus.langchain4j.agentic.dev-ui.enabled=false removes the card, JSON-RPC service, listener and decorators
- [x] #2 History size per system and payload truncation size are configurable
- [x] #3 Every option is documented in the generated config reference and covered by a test
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime: @ConfigMapping/@ConfigRoot(RUN_TIME) quarkus.langchain4j.agentic.dev-ui: history-size (20), max-payload-size (2048), javadoc'd; extension processor added to the runtime compiler so it is registered and documented.
2. deployment: @ConfigRoot(BUILD_TIME) same prefix: enabled (true); BooleanSupplier AgenticViewerEnabled added to every dev-only build step (recorder, beans incl. listener/decorators via RunObservation, pattern adapters, card, JSON-RPC).
3. RunManager takes the run-time config (no more ConfigProvider).
4. Tests: dev mode with enabled=false (no card, no JSON-RPC method, none of our beans); dev mode with history-size=2 and max-payload-size=40 (history bounded, payload truncated over JSON-RPC); config doc generated with the three keys.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
builder-discovery option moved to T5.1 (TASK-52) with builder support deferred (decision-2).

Config roots: runtime AgenticViewerRuntimeConfig (RUN_TIME: history-size 20, max-payload-size 2048), deployment AgenticViewerBuildConfig (BUILD_TIME: enabled true); the extension processor now also runs on runtime, and target/quarkus-config-doc of both modules lists the three keys with javadoc, phase and default. enabled=false: AgenticViewerEnabled added to the 5 dev-only build steps (recorder, runtime-dev beans incl. RunObservation which attaches listener/decorators, pattern adapters, card, JSON-RPC). RunManager reads the run-time mapping (ConfigProvider removed). Tests: DisabledDevUiDevModeTest (no Agentic Systems page, none of AgenticViewerJsonRpcService/AgenticSystemRegistry/RunManager/RunObservation/PatternAdapters in ArC's beans while the app's are), RunConfigDevModeTest (history-size=2 keeps the 2 newest of 3 runs; max-payload-size=40 truncates the run input over JSON-RPC). Default build: deployment 113, integration-tests 69.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All options are config roots under quarkus.langchain4j.agentic.dev-ui (enabled at build time; history-size and max-payload-size at run time), documented by the extension processor and covered by dev-mode tests; enabled=false removes the card, JSON-RPC service, listener and planner decorators.
<!-- SECTION:FINAL_SUMMARY:END -->
