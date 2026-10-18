---
id: DRAFT-3
title: 'Run part of a system: run only this node, or run from here'
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 06:13'
labels:
  - idea
  - dev-ui
  - runs
  - postponed
dependencies: []
priority: low
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
To check a change to one agent, the developer wants to run just that agent (or the end of the workflow from it) with the inputs it had in a run that already went through, instead of the whole system.

## Scope
From a node of a past run:
- "Run this node": runs only that sub-agent (or sub-workflow) with its scope seeded from the past run's state at the moment it started; the result is shown as a run of its own (timeline, graph highlighting of that subtree).
- "Run from here": runs the system again with every agent before the node pinned to its past output (DRAFT-4), so only the node and what follows actually run.
Out of scope: application calls; changing the topology.

## Shared building block (research 2026-10-18)
No public interception API exists (AgentListener callbacks are void, no veto or around hook, also in the 1.23 snapshot). One internal mechanism serves DRAFT-3, 4 and 5:
- Extend the PlannerDecisions decorator to the built-in planners too: in a Dev UI run (listeners.owner(scope) at init), rewrite each AgentExecutor of an Action.AgentCallAction to new AgentExecutor(new DelegatingInvoker(original invoker), agent). This single point covers AI, non-AI, nested workflows, A2A/MCP, supervisor and pattern choices and parallel mapper items (which the sub-agent list swap of HumanInTheLoopSteps misses: ParallelMapperPlanner builds MapperAgentInvoker executors itself). Agent ids are kept, so node correlation is unchanged.
- The invoker only has to return a value of the agent's output type: AgentExecutor writes it under the output key, records the invocation and notifies the planner; async agents are wrapped automatically. It must fire before/after listener events itself, as the human-in-the-loop DevUiStep does.
- New internal names go in LangChain4jInternals; guarded so an incompatible langchain4j only disables the feature.
- Upstream ask to record (next to U3-U5): an around-invocation hook on AgentListener returning a replacement output, and a public scope-seeding entry point.

## Specific design (research)
- Run a node alone through its AgentExecutor from the agent tree, on a fresh ephemeral scope (execute(scope, null)), never through a CDI bean or createAgenticSystem on the sub-agent class: a langchain4j agent has one parent, re-parenting breaks the declarative system.
- Seed the scope in afterAgenticScopeCreated (runs before the arguments are written). DevUiAgentListener must bind that scope to the new run and emulate the root call end (DelayedResponse resolution, error handler).
- "Run from here" is DRAFT-4 applied to the agents before the node; restarting a sequence at a cursor through the planner state key (__planner_state_<agentId>) is possible but relies on a private convention: avoid.

## Prerequisite
The run events must hold the scope state each agent started with: today only the agents' output keys are recorded (ScopeStateChanged), not keys written otherwise (writeState in non-AI agents, inner keys of nested workflows) nor the run inputs as scope entries. Record a bounded snapshot of the scope at each agent start, or the keys it reads.

## Open questions
- Behaviour when the path depends on state (loops, conditionals, supervisors): "run from here" may take another path than the past run.
- Results of a node run: kept in the history as a separate kind of run?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 From a node of a past run, 'Run this node' runs only that agent or sub-workflow with the scope state it had when it started in the past run
- [ ] #2 From a node of a past run, 'Run from here' re-runs the system with the agents before the node pinned to their past outputs (DRAFT-4)
- [ ] #3 The partial run appears in the history, marked as partial with its origin run and node; its timeline and graph show only what ran
- [ ] #4 The run events record what is needed to restore an agent's starting scope state (bounded by max-payload-size)
- [ ] #5 Application calls are never affected; tests cover a leaf agent, a nested workflow node and 'run from here' in a sequence
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it needs internal langchain4j hooks (no public API yet). Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
