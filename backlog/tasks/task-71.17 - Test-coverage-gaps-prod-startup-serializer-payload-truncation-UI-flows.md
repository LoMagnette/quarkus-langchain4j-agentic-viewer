---
id: TASK-71.17
title: 'Test coverage gaps: prod startup, serializer, payload truncation, UI flows'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
  - testing
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: chore
ordinal: 123000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
No test starts the packaged prod application (ProdArtifactIT only checks classes, and the @QuarkusTest class path contains runtime-dev, so a non-dev build step referencing runtime-dev would only fail at prod startup). The default build never sends events with real payloads (agent, tool, scope, planner) through the Dev UI serializer (ExecutionEventJsonTest uses a plain ObjectMapper). PayloadTruncatorTest misses the extra-fields marker, surrogate pairs at the cut, the 80-char floor of max-payload-size, and has a test accepting two results. Browser tests miss human input, cancel, data-flow view, node details, failed run, supervisor and pattern views, contributed topology, theme switch overlay.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A @QuarkusIntegrationTest starts the packaged prod app and calls an endpoint running an agentic system
- [x] #2 A dev-mode test streams a run with agent, tool, scope and planner events through the Dev UI websocket and checks their JSON
- [x] #3 PayloadTruncatorTest covers the listed cases with exact expectations
- [x] #4 Browser tests cover answering a human step, cancel, a failed run and the data-flow view
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ProdStartupIT (@QuarkusIntegrationTest): the packaged app starts; on the JVM package it runs a declarative system (new /test/research endpoint) and the builder samples.
2. Dev UI websocket JSON: a browser test (ui-tests, dev mode) captures the websocket frames of a run of a new ToolPlanner fixture (custom planner, AI agent with a tool) and checks agent, tool, scope and planner events as the Dev UI serializer wrote them.
3. PayloadTruncatorTest: extra-fields marker, surrogate pairs at the cut (fix in PayloadTruncator.cut), the 80-char floor, exact expectations for nested objects.
4. Browser tests: human step answered in the run panel (ApprovalWorkflow fixture), failed run (echo model fails on a topic), data-flow view (cancel done in TASK-71.10).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Findings: the failed-run browser test showed the run panel only gave langchain4j's wrapper message (Failed to invoke agent method...): ErrorInfo now carries the root cause, shown as 'Caused by <class>: <message>' in the run panel and Output tab. aCutNeverSplitsACharacter failed before (cut at 173 inside an emoji). Native: the sample app's agentic systems do not run in a native image (quarkus-langchain4j-agentic 1.14.2 registers no reflection for agent interfaces: 'No agent method found in class'), so ProdStartupIT checks only startup there, the system runs on the JVM package (documented in the test). The 'dev-mode test through the Dev UI websocket' AC is met by the browser test (a QuarkusDevModeTest driving Chromium, frames of the real websocket): the default deployment fixtures have no model provider for AI agents with tools.

Final verification: mvn -o clean install three rounds in a row in both configurations, all green (deployment 151/155, integration 106/127, failsafe 6 incl. ProdStartupIT); ui-tests 22/22; native container build with NativeArtifactIT and ProdStartupIT green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Coverage gaps closed: ProdStartupIT starts the packaged prod app (JVM: declarative and builder systems run; native: startup), a browser test checks agent, tool, scope and planner events as JSON on the Dev UI websocket, PayloadTruncatorTest covers markers, surrogate pairs (bug fixed) and the size floor with exact expectations, and browser tests cover a human step, a failed run (now showing its root cause) and the data-flow view.
<!-- SECTION:FINAL_SUMMARY:END -->
