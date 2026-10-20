---
id: TASK-88
title: >-
  Typed builder agents: resolve the agent method inherited from a parent
  interface
status: Done
assignee: []
created_date: '2026-10-20 16:04'
updated_date: '2026-10-20 16:21'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 144000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (medium): `CdiRunInvoker.agentMethod` (run of a typed builder system) and `BuilderTopology.declaredArguments` (its inputs) keep only abstract methods declared on the agent type itself, so `ChildAgent extends BaseAgent` with the agent method on `BaseAgent` has no candidate: it cannot run from the Dev UI and shows no inputs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The single abstract agent method is found across inherited interfaces; default, static and Object methods and the methods of langchain4j interfaces (e.g. AgenticScopeAccess) are not candidates
- [x] #2 A method redeclared by the sub-interface counts once
- [x] #3 Both the run and the topology inputs use the same rule (one shared helper)
- [x] #4 A typed builder system whose interface inherits its agent method runs from the Dev UI path (integration test)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. One helper in runtime-dev builder: abstract public methods of type.getMethods(), minus those declared by dev.langchain4j types, grouped by name + parameter types keeping the most specific; Optional of the only one. 2. CdiRunInvoker.agentMethod and BuilderTopology.declaredArguments use it. 3. Unit test of the rule; integration test: a builder system typed by an interface inheriting its method, run through RunManager.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
New runtime-dev builder/TypedAgentMethod: abstract public methods of type.getMethods(), minus bridge/synthetic, Object signatures (also redeclared abstractly) and methods declared by dev.langchain4j types; grouped by name + parameter types keeping the most specific declaration. CdiRunInvoker.agentMethod and BuilderTopology.declaredArguments use it. Tests: TypedAgentMethodTest (own, inherited, covariant redeclaration, default/static/toString, AgenticScopeAccess, two methods), BuilderRunsTest.typedBuilderInterfaceRunsThroughAnInheritedMethod (InheritingWriter extends BaseWriter, AgenticScopeAccess: inputs and run). Validation: mvn -o clean install 197/107/6 (1 skipped); mvn -o clean install -Ppatterns 206 (1 skipped)/145/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A typed builder agent whose method is inherited from a parent interface now shows its inputs and runs from the Dev UI: one shared helper resolves the single abstract agent method across interfaces, excluding default, static, Object and langchain4j methods. Verified with a unit test of the rule, an integration test running such a system and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
