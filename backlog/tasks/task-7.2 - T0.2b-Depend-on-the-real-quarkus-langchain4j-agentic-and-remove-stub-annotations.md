---
id: TASK-7.2
title: >-
  T0.2b: Depend on the real quarkus-langchain4j-agentic and remove stub
  annotations
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 08:25'
labels:
  - build
  - integration-tests
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-7
priority: high
type: task
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.2. The integration-tests module defines stub copies of `Agent`, `SequenceAgent`, `ParallelAgent`, `LoopAgent`, `ConditionalAgent`, `Output` and `AgenticServices` in package `io.quarkiverse.langchain4j.agentic`. All discovery and runtime work must target the real `quarkus-langchain4j-agentic` / `langchain4j-agentic` APIs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 quarkus-langchain4j-agentic is a managed dependency of the extension and used by integration-tests
- [x] #2 The stub annotation and AgenticServices classes are removed from integration-tests
- [x] #3 mvn clean install -DskipTests succeeds
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Align versions: import quarkus-langchain4j-bom 1.14.1 (latest stable; 2.0.0.Beta1 needs Quarkus 4 beta) and bump Quarkus 3.30.8 -> 3.33.3.1, the version 1.14.1 is built on.
2. runtime depends on quarkus-langchain4j-agentic, deployment on quarkus-langchain4j-agentic-deployment.
3. Delete the stub classes in integration-tests and point samples at dev.langchain4j.agentic(.declarative).
4. Fix whatever the real build-time validation rejects in the samples.
5. Point the prototype ImperativeAgentScanner at the real AgenticServices API so it keeps working until TASK-26.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validation: mvn clean install (with tests) succeeds, 6 tests pass. Prod quarkus-app lib contains langchain4j-agentic 1.20.2-beta30, quarkus-langchain4j-agentic 1.14.1 and the viewer runtime jar; still no runtime-dev class.

Findings from quarkus-langchain4j-agentic build-time validation (AgenticProcessor#validateAgenticParameterTypes):
- Every agent parameter must be an output key of some agent, a supervised key, or a user-provided key. A key is user-provided only when the root agent interface is injected somewhere (CDI injection points). Added SampleWorkflows, which injects the three root workflows.
- Output keys of non-AI agents (classes with @Agent methods) only count when the class is listed in a declarative subAgents. A chain of non-AI agents composed only through the builder API fails validation.
- An AI agent needs a ChatModel; with no provider extension, a user-defined ChatModel bean is accepted. Added PlaceholderChatModel (to be replaced by the scripted fake in TASK-8.1).

Sample changes: SampleAgents and ParallelAgentExample restructured so sub-agents are separate nested interfaces listed in subAgents, with static @Output methods (the real API requires both). MixedPatternExample is now a builder sequence over the declarative StepOne/StepTwo interfaces (a valid mixed system).

Prototype ImperativeAgentScanner: owner is now dev/langchain4j/agentic/AgenticServices and it captures every class passed to the varargs subAgents(...) instead of the stub add(Class).
Left as is: the *_ALT DotNames for the removed stub annotations in DeclarativeAgentScanner (dead code; that scanner is reworked in T1.2 / TASK-18).
Not verified here: the prototype view still finding the samples in dev mode (its build step only runs in dev mode); checked in TASK-7.3.

Follow-up fixed in TASK-7.3: switching to the real library made both prototype scanners list langchain4j internals (extra agents and imperative locations). Fixed by skipping dev.langchain4j.* annotations and langchain4j library archives.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The extension now builds on the real quarkus-langchain4j-agentic: quarkus-langchain4j-bom 1.14.1 imported, Quarkus bumped to 3.33.3.1, runtime/deployment depend on quarkus-langchain4j-agentic(-deployment). Stub annotations removed from integration-tests; samples ported to the real API and restructured to pass its build-time validation (nested sub-agent interfaces, static @Output, injected root workflows, placeholder ChatModel). Prototype ImperativeAgentScanner retargeted to the real AgenticServices API. Verified with mvn clean install (6 tests pass) and inspection of the prod package.
<!-- SECTION:FINAL_SUMMARY:END -->
