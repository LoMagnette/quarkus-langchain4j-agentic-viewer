---
id: TASK-57.2
title: 'T5.5b: Merge mixed systems with build-time declarative metadata'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 13:56'
labels:
  - runtime-dev
  - builder
  - topology
  - deferred
milestone: m-5
dependencies:
  - TASK-19
documentation:
  - spec-breakdown.md
parent_task_id: TASK-57
priority: low
type: feature
ordinal: 92000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T5.5. A builder workflow using declarative agent classes (or the reverse) is discovered at runtime, but its declarative parts have richer build-time metadata (prompts, guard method names).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Systems mixing builder and declarative parts are tagged MIXED
- [x] #2 Declarative sub-agents inside builder systems carry their build-time details (prompts, tools, guard names)
- [x] #3 Both mixed samples render with merged metadata
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Owner's rule: builder system MIXED when it uses an agent class of a declarative system; declarative system MIXED when one of its agent beans injects a builder system.
2. Build time: TopologyBuilder.agentClasses (node id -> class), MixedSystems (Arc injection points resolving to builder system beans), recorder gets the node of each agent class.
3. Runtime: BuilderTopology takes a declarative node lookup: shared agents take the build-time aiDetails/remoteDetails, system MIXED.
4. Tests: topology test (MIXED with lookup, BUILDER without, builder-only agents BUILDER), dev-mode fixture (declarative workflow injecting a builder system), samples in dev mode and browser.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Owner decision (Oct 17): a builder system is MIXED when it uses an agent interface that a declarative system of the app also uses; BUILDER when its agents are used by builders only.

Guard names of declarative sub-workflows inside builder systems: not applicable under Quarkus (a declarative workflow bean cannot be a builder sub-agent, T5.2 finding).
Verification: BuilderTopologyTest 7/7 (shared agents' aiDetails equal the build-time ones); BuilderDiscoveryDevModeTest (DelegatingWorkflow MIXED); sample app in dev mode: builderStoryCreator, BuilderStoryWriter, builderDraftReviewer and BlurbedStory MIXED, the rest DECLARATIVE; browser: 'mixed system defined in ...', shared agent details with its prompt. Also fixed T5.4: BuiltSystems held weakly (per-request systems vanished after GC), now strongly; BuilderTransformDevModeTest forces a GC. Full builds: default deployment 125 + integration 93, -Ppatterns 129 (1 skipped) + 112.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Mixed systems are tagged MIXED by the owner's rule (builder systems sharing declarative agents, declarative systems injecting builder systems) and the shared agents carry their build-time details. Verified with topology and dev-mode tests, the samples in dev mode and the browser. Also made the built-system registry hold instances strongly so per-request systems survive garbage collection.
<!-- SECTION:FINAL_SUMMARY:END -->
