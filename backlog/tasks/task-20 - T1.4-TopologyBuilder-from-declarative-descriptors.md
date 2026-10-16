---
id: TASK-20
title: 'T1.4: TopologyBuilder from declarative descriptors'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:36'
labels:
  - runtime-dev
  - topology
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.4. `TopologyBuilder` turns descriptors (and later live `AgentInstance` trees, T5.2) into the shared topology model, assigning stable node ids that runtime events will be correlated with (T2.6).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 TopologyBuilder produces an AgenticSystem for each declarative root with origin DECLARATIVE and a sourceRef
- [x] #2 Node ids are stable across builds of the same code
- [x] #3 Unit tests cover every node kind, including nesting (loop inside sequence)
- [x] #4 The AgenticSystem of each root is handed to the Dev UI as build-time data and to runtime-dev via a recorder (moved from TASK-19)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move AiAgentDetails and ToolDescriptor (renamed ToolInfo, like PlannerInfo) from deployment/scan to runtime/topology; AgentNode gains a nullable aiDetails field (prompts, model, tools for T1.7). The descriptor references them, as it already does Guard.
2. deployment/topology/TopologyBuilder (declarative, pure): one AgenticSystem per AgentTree root. System id = root node id = descriptor id (FQCN#method); child node id = parent id + '/' + sub-agent simple class name, '~2', '~3' for a class repeated among siblings. Ids only depend on class/method names, so they are stable across builds. Name: simple class name for the system, descriptor name for nodes. origin DECLARATIVE, sourceRef(class, method, null).
3. Node mapping: kind, description, keys, flags, aiDetails; loop guard on LOOP nodes; activation guard from the parent's branchGuards on conditional branches; PLANNER plannerInfo(plannerClass null, CUSTOM, extras supplier=Class#method), pattern detection is T4.2; unresolved sub-agent class -> NON_AI_AGENT leaf named after its class with an explanatory description.
4. inputs: empty for now, T1.9 generates them with JSON schemas.
5. Processor: AgenticSystemsBuildItem; dev only: recorder.setSystems (STATIC_INIT) in runtime-dev and card build-time data 'agenticSystems'.
6. Tests: builder unit tests per node kind incl. loop inside sequence and id stability/uniqueness; dev mode tests for the build-time data (DevUIBuildTimeDataTest) and the recorded systems (test route reading the recorder).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design note from TASK-18.1: the declarative descriptors (AgentDescriptor) are build-time records in the deployment module. Building the declarative topology at build time and recording AgenticSystem values avoids duplicating descriptors in runtime; the runtime-dev TopologyBuilder is then only needed for AgentInstance trees (T5.2, builder systems).

Implemented per plan. AiAgentDetails and ToolDescriptor moved to runtime/topology (ToolDescriptor renamed ToolInfo); AgentNode gained aiDetails; PlannerInfo.plannerClass is null for declarative planners, extras.supplier = Class#method. TopologyBuilder (deployment/topology) → AgenticSystemsBuildItem; recordSystems (dev only, STATIC_INIT) into AgenticViewerRecorder.setSystems/getSystems; registerDevUI adds build-time data 'agenticSystems'. Node ids: root = descriptor id (Class#method), child = parent + '/' + sub-agent simple class name, '~n' for repeated siblings; only names are used, so ids are stable across builds (TopologyBuilderTest rebuilds from a fresh scan and compares). Known limits for later tasks: AgentNode has a single guard, so a LOOP that is also a conditional branch keeps its exit guard and loses the activation one; an unguarded conditional branch is kept as a child although langchain4j drops it, which T2.6 must handle if it correlates children by position; unresolved sub-agent classes (e.g. A2A/MCP client agents, not scanned yet) become NON_AI_AGENT leaves described as 'No agent method found in <class>'. Validation: deployment 52 tests (TopologyBuilderTest 15, AgenticSystemsDevModeTest 2: DevUIBuildTimeDataTest + test route reading the recorder in dev mode, prod check AgenticViewerOutsideDevModeTest still passes), integration-tests 35, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Declarative root systems are turned into AgenticSystem topologies at build time, with stable name-based node ids, guards, planner supplier, flags and AI agent details on the nodes; in dev mode they are recorded into runtime-dev and passed to the Dev UI as build-time data. Verified by TopologyBuilderTest (every node kind, loop inside sequence, id uniqueness and stability) and AgenticSystemsDevModeTest (build-time data and recorded systems in a dev-mode app).
<!-- SECTION:FINAL_SUMMARY:END -->
