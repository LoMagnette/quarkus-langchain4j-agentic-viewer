---
id: TASK-46
title: >-
  T4.1: Detect langchain4j-agentic-patterns at build time and register adapters
  conditionally
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 19:59'
labels:
  - deployment
  - patterns
milestone: m-4
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 73000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T4.1 (F5). Covers the open question "Is there a dedicated Quarkus extension for the patterns module, or do apps add langchain4j-agentic-patterns directly?", which decides how detection works. Without the module, planner nodes must fall back to the generic view (F2.4) with no errors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The open question on how apps add the patterns module is answered and recorded
- [x] #2 Pattern adapters are registered only when langchain4j-agentic-patterns classes are on the classpath at build time
- [x] #3 An app without the module starts with no errors, warnings or extra beans
- [x] #4 Pattern adapter classes are never loaded when the module is absent
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Answer recorded (TASK-8.4): no Quarkus extension for langchain4j-agentic-patterns; apps add dev.langchain4j:langchain4j-agentic-patterns directly, so detection looks for its classes.
2. runtime-dev: optional dependency on langchain4j-agentic-patterns; patterns.PatternAdapter (no patterns types in its signature: pattern type, handles(planner), decision extras) and PatternAdapters (list, adapterFor(planner)); GoapAdapter, P2PAdapter, BlackboardAdapter skeletons (instanceof the patterns planner, tag decisions with the pattern; details come with T4.3-T4.5).
3. deployment (dev only): build step checking QuarkusClassLoader.isClassPresentAtRuntime for each pattern planner class and registering only the matching adapter beans plus the always-present PatternAdapters bean (collects Instance<PatternAdapter>); adapter classes are referenced nowhere else, so they are never loaded without the module.
4. DevUiAgentListener gets the PatternAdapters (RunObservation injects them); ReportingPlanner adds extras.pattern from the adapter of its planner.
5. Tests: unit test of the adapter selection (present / absent); deployment dev-mode test without the module: starts with no viewer warnings/errors and no adapter bean; patterns profile: decisions of GOAP/P2P/blackboard carry their pattern; dev mode with -Ppatterns checked through JSON-RPC.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Answer to the spec open question (from TASK-8.4): there is no Quarkus extension for the patterns module in quarkus-langchain4j 1.14.1; apps add dev.langchain4j:langchain4j-agentic-patterns directly (1.20.2-beta30 matches langchain4j-agentic). Detection must therefore look for the library classes (e.g. dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner). The module now also contains BDI, Debate and Voting planners, which the spec does not cover.

Answer (AC1) recorded in the task notes, CLAUDE.md and the progress summary: no Quarkus extension for langchain4j-agentic-patterns in quarkus-langchain4j 1.14.1, apps add dev.langchain4j:langchain4j-agentic-patterns directly; detection checks its planner classes (QuarkusClassLoader.isClassPresentAtRuntime). Verified: PatternAdaptersSelectionTest (none / all / only GOAP; adapter names are PatternAdapter classes, loaded unlinked); AgenticSystemsDevModeTest.noPatternAdapterWithoutTheModule (dev mode without the module: PatternAdapters bean only, no adapter bean in ArC's bean list, no pattern logger records or NoClassDefFoundError); PatternPlannerDecisionsTest (patterns profile: decisions tagged GOAP, P2P, BLACKBOARD) and PlannerDecisionEventsTest (custom planner untagged); dev mode with -Ppatterns (scratchpad pw/patterns.mjs): adapters registered by the build step, the three pattern samples' decisions tagged, DraftReviewer untagged, no viewer warnings. Builds: deployment 104, integration-tests 68; patterns profile pattern tests pass. BDI, Debate and Voting planners of the module are not covered by the spec and get the generic view.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The Dev UI detects langchain4j-agentic-patterns at build time (apps add it directly, no Quarkus extension exists) and registers GOAP, P2P and blackboard adapters only for the planner classes present; adapters are referenced by name only, so without the module nothing is loaded, registered or logged. Pattern planners' decisions now carry their pattern. Verified by unit, dev-mode and patterns-profile tests and a dev-mode run with the module.
<!-- SECTION:FINAL_SUMMARY:END -->
