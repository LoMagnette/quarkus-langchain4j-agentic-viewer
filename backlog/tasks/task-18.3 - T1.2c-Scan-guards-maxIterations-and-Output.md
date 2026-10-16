---
id: TASK-18.3
title: 'T1.2c: Scan guards, maxIterations and @Output'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:16'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F2.3). Loops and conditionals are only readable in the graph if their guards are named. Declarative guards are methods, so the method name is the label.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 @ActivationCondition and loop exit-condition methods are recorded as Guard labels with their method names
- [x] #2 maxIterations and testExitAtLoopEnd are recorded for loops
- [x] #3 @Output methods are recorded on their workflow
- [x] #4 Deployment tests cover a conditional and a loop fixture
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Upstream semantics (langchain4j-agentic 1.20.2-beta30, read from bytecode): @ExitCondition/@ActivationCondition/@Output are static methods on the workflow's declaring class; a conditional sub-agent gets the first @ActivationCondition method listing its class (none = dropped by upstream); loop maxIterations defaults to 10, testExitAtLoopEnd comes from @ExitCondition (default false).
2. AgentDescriptor: add loopGuard (runtime Guard: exit method name or null, maxIterations, testExitAtLoopEnd), branchGuards (sub-agent class -> Guard.activation(method name)) and outputMethod (Class#method, workflows only).
3. AgenticAnnotationScanner fills them for LOOP, CONDITIONAL and workflow kinds.
4. Fixtures: loop with @ExitCondition(testExitAtLoopEnd) + @Output, conditional with two branches (one shared condition) and a branch without condition; unit test fields and extend the build-step summary.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Upstream semantics read from langchain4j-agentic 1.20.2-beta30 bytecode (AgenticServices.buildConditionalAgent, LoopAgentServiceImpl, DeclarativeUtil.configureOutput): guards and @Output are static methods of the workflow's declaring class; a conditional branch uses the first @ActivationCondition listing its sub-agent class, and a sub-agent with none is silently dropped by langchain4j (the scanner keeps it in subAgents without a branch guard; T1.4 decides how to show it). AgentDescriptor gained loopGuard (Guard.loopExit(exit method or null, maxIterations default 10, testExitAtLoopEnd default false)), branchGuards (sub-agent class -> Guard.activation(method), in sub-agent order) and outputMethod (Class#method, workflow kinds only). Labels are method names; the annotations' description attribute (default "<unknown>") is not read. Fixtures: Polisher loop with @ExitCondition(testExitAtLoopEnd) and @Output, Repeater loop with defaults, Router conditional with two guarded branches; test-only Triage with a shared condition and an unguarded branch. Validation: mvn install -pl runtime,runtime-dev,deployment (deployment 25 tests: scanner unit 16, build step 1 with 21 descriptors) and mvn verify -pl integration-tests (30 tests), all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The annotation scanner records loop exit guards (method name, maxIterations, testExitAtLoopEnd), per-branch activation guards of conditionals and the @Output method of workflows on AgentDescriptor, matching langchain4j's resolution rules. Verified by scanner unit tests on loop and conditional fixtures (including defaults, a shared condition and an unguarded branch) and the QuarkusUnitTest build step on a real fixture app; integration tests still pass.
<!-- SECTION:FINAL_SUMMARY:END -->
