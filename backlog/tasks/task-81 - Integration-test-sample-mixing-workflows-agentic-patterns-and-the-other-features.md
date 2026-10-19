---
id: TASK-81
title: >-
  Integration-test sample mixing workflows, agentic patterns and the other
  features
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 17:29'
updated_date: '2026-10-19 17:43'
labels: []
dependencies: []
priority: medium
type: task
ordinal: 137000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Each sample of the integration tests shows one pattern on its own (ProductLaunch the workflows, one sample per agentic-patterns planner). The user asked for a sample that mixes workflows, agentic patterns and a bit of everything, to see and test how the viewer handles pattern planners nested in workflows (node ids, decisions, views, tags) next to tools, a human step and @Output.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A declarative sample in the patterns profile nests several langchain4j-agentic-patterns planners inside workflows (sequence, parallel, loop, conditional), with an AI agent using a tool, a @HumanInTheLoop step and @Output methods
- [x] #2 It runs end to end on the scripted fake model, called directly and as a Dev UI run (RunManager, human step answered)
- [x] #3 In a Dev UI run, the decisions of the nested planners carry their pattern and node ids of the topology
- [x] #4 The topology tags the nested planners with their pattern; existing pattern tests still pass
- [x] #5 Documented with the samples (CLAUDE.md / docs where the samples are listed)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. integration-tests/src/patterns/.../FeatureDelivery.java: root @SequenceAgent: Discovery (@ParallelAgent: UserResearcher, CodeScout with a @ToolBox tool, @Output findings) -> SpecPlanner (GOAP: requirements, risks, spec; estimator and UX sketcher not needed, never called) -> Implementer -> ReviewLoop (@LoopAgent: CodeReview voting planner of 3 reviewers, Refactorer; exit when the verdict is APPROVE) -> ProductOwnerSignOff (@HumanInTheLoop) -> Rollout (@ConditionalAgent: ReleaseTrain BDI planner when approved, Postponer otherwise) -> root @Output. Script on the fake model, injected in PatternSampleWorkflows.
2. Tests: PatternSamplesTest (direct call: prompts in order, human yes / no), PatternSampleRunsTest (RunManager run), PatternPlannerDecisionsTest (decisions of the 3 nested planners tagged, node ids from the topology, REJECT then APPROVE), PatternPlannerTypesTest (nested planner types, 24 nodes, summary; root map limited to planner roots).
3. Docs: CLAUDE.md samples line, AGENT/PROGRESS-SUMMARY.md (index.adoc lists no samples).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Checked in dev mode (-Ppatterns, port 8089): no 'do not agree' warning; screenshot of the graph: GOAP, voting and BDI views render nested in the sequence, loop and conditional, header tags 'GOAP Voting BDI'. The screenshot showed the three reviewers all labelled 'vote' (node names are method names): renamed voteOnReadability / voteOnSecurity / voteOnPerformance.
Validation: mvn -o clean install (deployment 185, integration-tests 106, ITs 6 / 1 skipped); -Ppatterns (194 / 1 skipped, integration-tests 144 = 139 + 5 new, ITs 6 / 1 skipped).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added FeatureDelivery to the patterns samples: a sequence with a parallel discovery (one agent calls a repository tool, @Output combines), a GOAP planner reaching the spec (estimate and sketch never made), a review loop around a voting planner (rejects v1, approves v2), a @HumanInTheLoop sign-off and a conditional rollout through a BDI planner (or a postponement); 24 nodes, scripted on the fake model. Tested called directly (prompt order, yes and no answers), as a Dev UI run, for the decisions of the three nested planners (pattern, node ids) and their topology tags; checked in dev mode (no upstream disagreement, graph renders). Default and patterns builds pass.
<!-- SECTION:FINAL_SUMMARY:END -->
