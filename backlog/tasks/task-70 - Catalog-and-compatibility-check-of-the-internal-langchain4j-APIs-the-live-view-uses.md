---
id: TASK-70
title: >-
  Catalog and compatibility check of the internal langchain4j APIs the live view
  uses
status: Done
assignee:
  - '@claude'
created_date: '2026-10-16 21:26'
updated_date: '2026-10-16 21:27'
labels:
  - compatibility
  - testing
dependencies: []
type: task
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The live view (runs, human-in-the-loop answers, planner decisions, @ErrorHandler events, pattern views) relies on internal classes, private fields and methods of langchain4j-agentic, langchain4j-agentic-patterns and quarkus-langchain4j-agentic, spread over several hooks. An upgrade of those libraries could silently disable parts of the live view; the dependencies need one list, a test failing on an incompatible upgrade, and a clear message for users running an incompatible version.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 One class lists every internal class, field, method and constructor used, with the library and what depends on it, and the hooks take their names from it
- [x] #2 A test fails, naming the missing parts, when a library on the build class path lacks one (pattern parts checked when the module is present)
- [x] #3 Dev mode logs one warning listing the missing parts and what they disable
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
LangChain4jInternals (runtime-dev runs): 29 dependencies (AGENTIC, QUARKUS_AGENTIC, PATTERNS) with what each enables; AgentTrees, HumanInTheLoopSteps, PlannerDecisions, ErrorHandlers, DevUiAgentListener, P2PAdapter and BlackboardAdapter take their names from it. problems(classLoader) checks classes, fields (hierarchy), methods (hierarchy + interfaces) and constructors by parameter type names; pattern entries only when the module is present. RunObservation logs one WARN at startup with the missing parts. Tests: LangChain4jInternalsTest (all present, default and -Ppatterns; a renamed field, a removed class and a changed method signature reported with their effect), PatternInternalsTest (-Ppatterns: P2P and blackboard executionState carry invocationCounter), AgenticSystemsDevModeTest.noInternalApiWarning.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Every internal langchain4j API the live view uses is listed in LangChain4jInternals, which the hooks take their names from; a test fails on an incompatible library naming what breaks, and dev mode logs the missing parts at startup. Verified in both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
