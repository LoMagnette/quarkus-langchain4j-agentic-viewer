---
id: TASK-43
title: 'T3.2: PlannerDecorator for declarative planners'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 19:07'
labels:
  - runtime-dev
  - planner
milestone: m-3
dependencies:
  - TASK-12
  - TASK-31
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T3.2. Builds on the T0.7 spike. Until U3 lands, the only way to see planner decisions is to wrap any `Supplier<Planner>` and report each `Action` from `firstAction` / `nextAction`. If U3 is merged first, this task can use the listener callbacks instead.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 PlannerDecorator wraps the planner supplier of every declarative (@PlannerSupplier) planner node
- [x] #2 A PlannerDecision event (nodeId, agents selected, done flag) is emitted for each Action
- [x] #3 Decision events are emitted for every planner in the sample app
- [x] #4 The decorator does not change the planner behaviour (sample outputs unchanged)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. AgentTrees (runs, internal API isolated): finds every PlannerBasedInvocationHandler under a root (Quarkus agent 'agent' field or JDK proxy, recursing through AbstractServiceBuilder.subagents); HumanInTheLoopSteps reuses it.
2. PlannerDecisions: replaces each handler's plannerSupplier (reflection, final field; guarded, logs once) with a supplier that wraps each fresh planner in a delegating DecisionReportingPlanner, except the built-in workflow planners (dev.langchain4j.agentic.workflow.impl: sequence, loop, parallel, mapper, conditional), so supervisors, @PlannerSupplier and pattern planners report. Installed by RootListeners.attach with the HITL steps.
3. The planner reports each Action of firstAction/nextAction (call, done, done with result, suspend; no-ops skipped) to DevUiAgentListener, which emits PlannerDecision for Dev UI runs: node and invocation of the planner workflow (open invocation of the planner agent), selected node ids from the position of each chosen executor in the planner's sub-agents (= topology children order), done flag, extras {action, result}.
4. Tests: integration test of decisions for BankSupervisor (exchange, withdraw, credit, done with result) and DraftReviewer (custom planner, 4 calls then done), outputs unchanged; event sequence test updated; patterns profile planners checked too.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scope reduced to declarative planners (decision-2); builder plannerBuilder wrapping is covered by T5.3.

Design input from TASK-12 (doc-4): install the decorator at dev-mode startup on each root by replacing PlannerBasedInvocationHandler.plannerSupplier (reflection), next to the T0.4 root listener registration; guard it so failures only disable decision events. Covers built-in planners (supervisor, sequence...) as well as @PlannerSupplier ones. Nested planner agents need a walk of subagents() (unverified).

Decorates every workflow's plannerSupplier in the root trees (AgentTrees walk, shared with HumanInTheLoopSteps) and skips the built-in workflow planners at supply time, so plain workflows are untouched (JsonRpcEventSequenceTest only gained the supervisor's decisions). Decorators chain when several Dev UI listeners are attached; each reports only its own manager's runs. Tests: PlannerDecisionEventsTest (supervisor exact decisions, node/invocation ids, each call decided before the agent starts, output and balances unchanged; DraftReviewer custom planner; none for built-in workflows), PatternPlannerDecisionsTest (patterns profile: GOAP, P2P, blackboard exact decision sequences, outputs unchanged), PlannerWrapSpikeTest still passes on top. Builds: default deployment 97 + integration-tests 68; patterns profile 77.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Supervisors, @PlannerSupplier planners and pattern planners report each decision of a Dev UI run as a PlannerDecision event (planner node and invocation, chosen node ids, done flag, action/result extras), through a delegating planner installed on every workflow's plannerSupplier at startup; built-in workflows are left untouched and planner behaviour is unchanged. Verified by PlannerDecisionEventsTest, PatternPlannerDecisionsTest (patterns profile) and the full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
