---
id: TASK-17
title: 'T1.1: Shared topology model records in runtime'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:29'
labels:
  - runtime
  - model
milestone: m-1
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.1. Build time (deployment), runtime (runtime-dev) and the UI must share one topology model so declarative, builder and mixed systems render identically. It lives in `runtime` because it is the only module present in prod; it must stay inert. Replaces the prototype records (`AgenticGraph`, `WorkflowComposition`, `DataFlow`, `ImperativePattern`).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Records AgenticSystem, AgentNode, Guard and PlannerInfo exist with exactly the fields in the spec Topology model table
- [x] #2 Enums cover origin (DECLARATIVE, BUILDER, MIXED), node kind (AI_AGENT ... HUMAN_IN_THE_LOOP) and pattern type (GOAP, P2P, BLACKBOARD, CUSTOM)
- [x] #3 The records serialize to and from JSON without custom code in the UI
- [x] #4 The records are usable as build-time recorded values
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Records in runtime, package ...viewer.runtime.topology (coexisting with the prototype model package until TASK-26): AgenticSystem, AgentNode, Guard, PlannerInfo, SourceRef, SystemInput; enums SystemOrigin, NodeKind, PatternType.
2. Interpretation of the spec table: AgenticSystem also carries the tree (root), with rootNodeId checked equal to root.id(); a LOOP node carries its exit-condition Guard; each branch of a CONDITIONAL node carries its activation-condition Guard (so the label sits on the edge into the branch); PLANNER nodes carry PlannerInfo. Inputs carry the JSON schema as JSON text.
3. Compact constructors: null lists become empty, lists are copied (immutable).
4. Tests (deployment module): Jackson round trip with a default ObjectMapper on the StoryCreator topology; recordability through a QuarkusUnitTest build step using BytecodeRecorderImpl + StaticBytecodeRecorderBuildItem and a test recorder, checked at runtime.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented in runtime, package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology (the prototype records in runtime/model stay until TASK-26 retires the Mermaid view): records AgenticSystem, AgentNode, Guard, PlannerInfo, SourceRef, SystemInput; enums SystemOrigin, NodeKind, PatternType.

Interpretation of the spec table (documented in the Javadoc):
- AgenticSystem also carries the tree (root); rootNodeId is kept as specified and checked equal to root.id().
- LOOP nodes carry their exit-condition Guard (label, maxIterations, testExitAtLoopEnd); each branch of a CONDITIONAL node carries its activation-condition Guard (maxIterations/testExitAtLoopEnd null), so the label belongs to the edge into the branch.
- PLANNER nodes carry PlannerInfo (plannerClass, patternType, extras as Map<String,String>).
- SourceRef = className + methodName (declarative) or callSite (builder); SystemInput = name, javaType, jsonSchema as JSON text.
- Compact constructors turn null lists/maps into empty ones and copy them (immutable).
- Not included yet: AI-agent details (prompts, model, tools) needed by the details panel; to be added with T1.2d/T1.7.

Validation: mvn clean install, all tests pass (deployment 8 incl. 5 new, integration-tests 30). TopologyJsonTest: round trip of two fixtures through a default ObjectMapper, plain field names (rootNodeId, kind "LOOP", guard fields), rootNodeId check, null lists. TopologyRecordingTest: a build step records the fixtures with BytecodeRecorderImpl/StaticBytecodeRecorderBuildItem and the runtime value equals the original (the recorder class has to be loaded from the build's TCCL, not the test's class loader).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added the shared topology model in runtime (package runtime.topology): AgenticSystem, AgentNode, Guard, PlannerInfo, SourceRef, SystemInput and the SystemOrigin/NodeKind/PatternType enums, inert and immutable. Verified JSON round trips with a default ObjectMapper and build-time recording through the bytecode recorder (TopologyJsonTest, TopologyRecordingTest).
<!-- SECTION:FINAL_SUMMARY:END -->
