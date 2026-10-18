---
id: DRAFT-5
title: Breakpoints and step mode in Dev UI runs
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
When a run goes wrong in the middle, the developer wants to stop before an agent, look at (and fix) the scope it is about to read, then go on, skip it or stop, without rerunning everything or adding logs.

## Scope
- Toggle a breakpoint on graph nodes (per system, for Dev UI runs); a step mode pauses before every agent.
- At a pause: the run panel shows the agent, its inputs and the scope state; the developer can edit scope values (JSON, converted to their types), then Continue, Skip (keep the current value of its output key) or Abort (cancel the run).
- The graph marks the paused node like a waiting human step.
Out of scope: application calls; pausing inside an agent (between model rounds).

## Shared building block (research 2026-10-18)
No public interception API exists (AgentListener callbacks are void, no veto or around hook, also in the 1.23 snapshot). One internal mechanism serves DRAFT-3, 4 and 5:
- Extend the PlannerDecisions decorator to the built-in planners too: in a Dev UI run (listeners.owner(scope) at init), rewrite each AgentExecutor of an Action.AgentCallAction to new AgentExecutor(new DelegatingInvoker(original invoker), agent). This single point covers AI, non-AI, nested workflows, A2A/MCP, supervisor and pattern choices and parallel mapper items (which the sub-agent list swap of HumanInTheLoopSteps misses: ParallelMapperPlanner builds MapperAgentInvoker executors itself). Agent ids are kept, so node correlation is unchanged.
- The invoker only has to return a value of the agent's output type: AgentExecutor writes it under the output key, records the invocation and notifies the planner; async agents are wrapped automatically. It must fire before/after listener events itself, as the human-in-the-loop DevUiStep does.
- New internal names go in LangChain4jInternals; guarded so an incompatible langchain4j only disables the feature.
- Upstream ask to record (next to U3-U5): an around-invocation hook on AgentListener returning a replacement output, and a public scope-seeding entry point.

## Specific design (research)
- Pause in the delegating invoker's toInvocationArguments (on the planner thread, before the agent's arguments are read), or recompute the arguments after resuming, so edits are seen.
- Reuse the human-in-the-loop waiting machinery (RunManager.awaitHumanInput-like wait, cancel and shutdown release it).
- DefaultAgenticScope state is a ConcurrentHashMap: writes from the JSON-RPC thread are safe; values must be converted to the original Java types; planner state keys (__planner_state_*) hidden; overwriting a pending DelayedResponse key would lose that result.
- Abort must not be an AgentInvocationException (the application's @ErrorHandler could retry or recover): cancel the run.

## Open questions
- Several pauses at once (parallel branches, mapper items) and the executor threads they hold.
- Async agents: pause or not?
- Record edits as run events (so a replay or a test from the run knows about them)?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A breakpoint can be set on any agent node; a Dev UI run pauses before that agent and the graph shows it paused
- [ ] #2 Step mode pauses before every agent invocation
- [ ] #3 At a pause the run panel shows the agent's inputs and the scope state; edited values (JSON) are converted to their types and seen by the agent when the run continues
- [ ] #4 Continue runs the agent, Skip keeps its output key's current value and moves on, Abort cancels the run; cancel and shutdown release a pause
- [ ] #5 Pauses in parallel branches or mapper items are shown and resumed independently
- [ ] #6 Application calls are never paused; tests cover continue, skip with an edit, abort and a parallel pause
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it needs internal langchain4j hooks (no public API yet). Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
