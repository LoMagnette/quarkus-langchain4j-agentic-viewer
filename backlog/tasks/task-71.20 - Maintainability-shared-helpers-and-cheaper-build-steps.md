---
id: TASK-71.20
title: 'Maintainability: shared helpers and cheaper build steps'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
  - refactoring
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: chore
ordinal: 126000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The node id simple-name rule exists in DevUiAgentListener, BuilderTopology and the build-time TopologyBuilder; runs only match the graph while they stay identical. RuntimeInputSchema and InputSchemaGenerator duplicate their type tables. In the Dev UI, LiveRun lives in the run panel, the timeline imports from it, and shortRef, simpleType, formatDuration, the node index walk and error message extraction are duplicated. BuilderSystemBeans re-reads class bytes on every isBuilderSystem call (from three places, once per node in MixedSystems), BuildCallTransformer and BuilderSystemBeans disagree on what an agentic build() is, the scanner computes JSON schemas for every agent though only roots use them, PlannerTypeDetector looks up planner implementors per NEW instruction, and the dev.langchain4j. prefix skips application code in such packages.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 One node id rule is shared by the build-time topology, the builder topology and the listener
- [x] #2 Both input schema generators share their type tables
- [x] #3 Dev UI run state and shared helpers live in one module used by all components
- [x] #4 Bytecode of a class is read at most once per build in BuilderSystemBeans and both build() checks agree
- [x] #5 All builds and tests pass unchanged
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime topology NodeIds: the node id rule (simple name, child id with ~n) used by TopologyBuilder, BuilderTopology and DevUiAgentListener.
2. runtime topology InputTypes: the type tables and the @Nullable rule shared by InputSchemaGenerator and RuntimeInputSchema.
3. Dev UI qwc-agentic-run-model.js: LiveRun and the shared helpers (errorText, shortRef, simpleClass, simpleType, formatDuration, TERMINATIONS, findNode, indexNodes), imported by run panel, timeline, output, node details and the systems page.
4. BuilderSystemBeans reads each class once (methods calling an agentic build() cached), one instance per build (BuilderSystemBeansBuildItem), and one isAgenticBuild definition shared with BuildCallTransformer.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Final verification: mvn -o clean install three rounds in a row in both configurations, all green (deployment 151/155, integration 106/127, failsafe 6 incl. ProdStartupIT); ui-tests 22/22; native container build with NativeArtifactIT and ProdStartupIT green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
One node id rule (NodeIds) and one set of input type tables (InputTypes) are shared by build time and runtime, the Dev UI run model and helpers live in qwc-agentic-run-model.js, and builder bytecode is read once per class per build with one definition of an agentic build(). All builds, browser tests and the native check pass unchanged.
<!-- SECTION:FINAL_SUMMARY:END -->
