---
id: TASK-78.2
title: 'Recognize the BDI, Debate and Voting planners of agentic-patterns'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 08:37'
labels:
  - review
  - patterns
  - deployment
  - runtime-dev
  - dev-ui
dependencies: []
parent_task_id: TASK-78
priority: high
type: feature
ordinal: 131000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Review finding 1. The pinned langchain4j-agentic-patterns has six planner families: GOAP, P2P, Blackboard, BDI (`bdi.BDIPlanner`, `Desire`), Debate (`debate.DebatePlanner`, `ConvergenceStrategy`) and Voting (`voting.VotingPlanner`, `VotingStrategy`). The viewer recognizes only the first three (`PlannerTypeDetector.PATTERN_PLANNERS`, `AgenticViewerProcessor.PATTERN_ADAPTERS`, `BuilderTopology`); the others show as CUSTOM planners with no dedicated view, decision details or tests, while the docs claim support of the patterns module.

## Constraint
Public API only (no new internal access): the planners expose `topology()`, BDI and Debate expose `executionState()`, `Desire` is a public record, Debate writes `DebatePlanner.DEBATE_CONTEXT_KEY` to the scope. What would need private planner state is left out and documented.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 BDI, Debate and Voting planners are detected as their own pattern type, for declarative @PlannerSupplier systems (including subclasses) and builder systems
- [x] #2 Each has a dedicated presentation of its sub-agents in the graph that tells their role (BDI desires and their agents when known, Debate debaters and judge, Voting voters)
- [x] #3 During a run, planner decisions of these patterns carry the pattern information available through public API (e.g. BDI current desire, Debate round and phase, Voting progress) and the reason the planner stopped when it can be told
- [x] #4 An adapter is only registered when its planner class is present: the default build (no patterns module) is unaffected
- [x] #5 A sample system per pattern in the patterns samples, with tests of detection, topology and a run, in the -Ppatterns build
- [x] #6 User documentation lists the six patterns and what is shown for each, including what is not available without internal access
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Public API only (no reflection on planner state). What each planner exposes: topology() (BDI SEQUENCE, Debate STAR, Voting PARALLEL); executionState() public for BDI (invocationCounter) and Debate (currentRound, lastDebatersMessages); Desire is a public record but the planner keeps its desires private; Debate takes the last sub-agent as judge (>= 3 sub-agents), Voting calls all sub-agents at once; Action.result() is public.
1. PatternType BDI, DEBATE, VOTING; PlannerTypeDetector, BuilderTopology and AgenticViewerProcessor.PATTERN_ADAPTERS know the three planner classes (and subclasses).
2. BDI desires at build time from the @PlannerSupplier bytecode (ASM, like the planner class): Desire.of(name, priority, achievableKey, satisfiedKey, Agent.class...) with literal arguments gives name, priority, the two keys (Desire.of with strings means scope.hasState(key)) and the agents; Desire.of with predicates gives name, priority, agents, conditions unknown; anything not literal: desires unknown. PlannerInfo gets a desires list (agents as child node ids, mapped by TopologyBuilder). Builder systems: desires unknown (private field), documented.
3. Graph: Debate = debaters side by side, a back-edge 'next round' and the judge after them; Voting = fork to the voters, join into a 'vote' tally node; BDI = one node per known desire (priority order, keys as label) linked to its agents in intention order, otherwise the generic hub.
4. Adapters (runtime-dev/patterns, registered only with the module): DebateAdapter (round, phase debate/judge, number of debater messages), VotingAdapter (voters, votes on done), BdiAdapter (invocations). Run model, node info and timeline show them; BDI current desire and satisfied desires inferred in the UI from the known desires (selected agent, written keys).
5. Node details: pattern labels; BDI desires; Debate judge; Voting voters.
6. Patterns samples: a declarative BDI, Debate and Voting system with scripted model replies; tests: PlannerTypeDetectorTest (desires), PatternPlannerTypesTest, a builder system per new pattern (detection), PatternSampleRunsTest / decision tests for the three, JS graph build checked through a UI-free test if one exists, else the dev-mode topology.
7. Docs (index.adoc, CLAUDE.md patterns section): six patterns, what each shows, what is not available without internal access (Debate max rounds / convergence reason, Voting strategy, BDI desires of builder systems).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented as planned, public API only. PlannerInfo.desires (BDI) from DesireReader, an operand-stack simulation of the @PlannerSupplier body (constants, class literals and arrays, List.of / Arrays.asList, lambdas as unknown values; jumps and unsupported instructions lose the stack): literal Desire.of / new Desire give the desires, anything else null. PlannerInfo needed @RecordableConstructor once it had two constructors. The debate sample cannot let debaters read debateContext as a parameter: quarkus-langchain4j's validateAgenticParameterTypes rejects a key no agent writes (documented in the sample and CLAUDE.md). Adapters: BdiAdapter (invocations), DebateAdapter (round, messages), VotingAdapter (voters; on done the votes from scope.agentInvocations(voter.name()) and the voted agents). UI: graph views desireGraph / debateGraph / votingGraph, node details (desires, debaters/judge, voters), run notes on the planner node (desire, round/judge, votes -> result, desires satisfied) and timeline step notes (patternNote, buildTimeline takes the system). Layout checked on a screenshot.
Validation: mvn -o clean install -Ppatterns BUILD SUCCESS (deployment 180 / 1 skipped, integration-tests 139, ITs 6 / 1 skipped); mvn -o clean install BUILD SUCCESS (171, 106, 6 / 1 skipped); UI tests 28/28 (new bdiDebateAndVotingPlannersShowTheRolesOfTheirAgents). New tests: DesireReaderTest, PatternDetectionTest, PatternAdaptersSelectionTest, PatternAdaptersDevModeTest (six adapters), PatternPlannerTypesTest (desires), BuilderPatternTypesTest, PatternSamplesTest / PatternSampleRunsTest / PatternPlannerDecisionsTest (bdi, debate, voting).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The BDI, debate and voting planners of langchain4j-agentic-patterns are recognized like GOAP, P2P and blackboard, for declarative and builder systems, with public API only. BDI desires are read at build time from literal Desire.of(...) calls of the @PlannerSupplier and drawn as desire nodes linked to their agents; debates draw debaters, a next-round back-edge and the judge; votes draw voters into a tally. Runs tell the desire pursued and the desires satisfied, the round and the judge, the votes and the result; what needs private planner state (debate max rounds and convergence reason, voting strategy, desires of builder systems) is documented as not shown. Three new samples (ReleaseCaptain, DesignDebate, ReviewPanel). Verified with both CI builds and the 28 UI tests.
<!-- SECTION:FINAL_SUMMARY:END -->
