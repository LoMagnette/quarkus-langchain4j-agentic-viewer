---
id: TASK-18.1
title: 'T1.2a: Scan workflow annotations, sub-agents and keys'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:35'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F1.1). Core structure of declarative systems: the agent and workflow annotations, their sub-agent lists and the AgenticScope keys they read and write.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Descriptors are produced for @Agent, @SequenceAgent, @ParallelAgent, @ParallelMapperAgent, @LoopAgent and @ConditionalAgent
- [x] #2 Each descriptor records its ordered sub-agents, input keys and outputKey
- [x] #3 Deployment tests (QuarkusUnitTest) cover each annotation on fixture interfaces
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. New scanner (deployment, package deployment.scan; the prototype DeclarativeAgentScanner stays until TASK-26): AgenticAnnotationScanner reads the combined Jandex index and returns AgentDescriptor records, skipping classes from dev.langchain4j.*.
2. AgentDescriptor: id (declaring class + "#" + method), declaringClass, methodName, kind (AI_AGENT when an abstract interface method carries @Agent, NON_AI_AGENT for a concrete method, else the workflow kind), name (annotation name or method name), description, outputKey, inputKeys (@V value or parameter name; AgenticScope and @MemoryId parameters skipped), subAgents (class names in declaration order; the single subAgent of @ParallelMapperAgent), itemsProvider (mapper).
3. Build step producing AgenticDescriptorsBuildItem.
4. Tests: scanner unit tests on a Jandex index of fixture interfaces (one per annotation); a QuarkusUnitTest consuming the build item in a real app with the same fixtures (wiring check, results reported through a system property).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented (deployment, package deployment.scan; prototype DeclarativeAgentScanner untouched until TASK-26):
- AgenticAnnotationScanner: reads @Agent, @SequenceAgent, @ParallelAgent, @ParallelMapperAgent, @LoopAgent and @ConditionalAgent from the combined index, skipping classes in dev.langchain4j.*; results sorted by id.
- AgentDescriptor: id (class#method), declaringClass, methodName, kind (AI_AGENT for an abstract interface @Agent method, NON_AI_AGENT for a concrete one, else the workflow kind), name (annotation name or method name), description (@Agent value used as description when description is empty), outputKey (typedOutputKey -> simple class name; an overridden TypedKey.name() is only known at runtime), inputKeys (@V or parameter name; AgenticScope and @MemoryId parameters skipped), subAgents (declaration order; mapper subAgent), itemsProvider.
- Build step scanAgenticAnnotations -> AgenticDescriptorsBuildItem (consumed by later Phase 1 steps).

Validation: mvn clean install, all tests pass (deployment 18 incl. 10 new, integration-tests 30). AgenticAnnotationScannerTest (9 tests, Jandex index of fixtures): every field per annotation. AgenticDescriptorsBuildStepTest (QuarkusUnitTest): a real app built from the same fixtures (valid for quarkus-langchain4j-agentic: roots injected, ChatModel bean) produces exactly the expected 14 descriptors.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added AgenticAnnotationScanner and the AgenticDescriptorsBuildItem build step: one AgentDescriptor per @Agent and workflow-annotated method, with kind, name, description, ordered sub-agents, input keys and output key. Covered by 9 scanner unit tests and a QuarkusUnitTest on a real fixture app.
<!-- SECTION:FINAL_SUMMARY:END -->
