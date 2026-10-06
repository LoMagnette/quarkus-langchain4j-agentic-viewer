---
id: TASK-7
title: >-
  T0.2: Extension skeleton with runtime, runtime-dev, deployment,
  integration-tests
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 08:25'
labels:
  - build
  - dev-ui
milestone: m-0
dependencies:
  - TASK-5
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.2.

The spec requires only an inert model module in production: build-time scanning in `deployment`, everything live in a dev-only `runtime-dev`, and an almost empty `runtime`. The repo currently holds a prototype (Mermaid view, ASM imperative scanner, Jandex scanner, stub annotation classes such as `io.quarkiverse.langchain4j.agentic.Agent` in integration-tests) with no `runtime-dev` module and no real `langchain4j-agentic` dependency. Subtasks restructure it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An empty Dev UI card for the extension shows in the sample app in dev mode
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
All three subtasks done (TASK-7.1 runtime-dev module, TASK-7.2 real quarkus-langchain4j-agentic dependency, TASK-7.3 Dev UI page + JSON-RPC). The extension card shows in the sample app in dev mode, verified with headless Chromium.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Extension skeleton in place: runtime (model only), dev-only runtime-dev (recorder, JSON-RPC service), deployment and integration-tests, on Quarkus 3.33.3.1 and quarkus-langchain4j 1.14.1. The Dev UI card shows in the sample app with a JSON-RPC-backed page. See the subtasks for details and verification.
<!-- SECTION:FINAL_SUMMARY:END -->
