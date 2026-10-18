---
id: DRAFT-4
title: Pin an agent output for the next runs
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
Iterating on one agent of a long system means re-running, waiting for and paying for every model call before it. Pinning the output of the agents that are fine makes each run fast and cheap and keeps the inputs of the agent being tuned stable.

## Scope
- Pin a node's output for Dev UI runs of its system: from a past run (its recorded output) or typed as JSON. Unpin one or all.
- A pinned agent does not run: it returns the pinned value (converted to its output type), written under its output key; the timeline and graph mark it as pinned (no model call, no tokens).
- Pins live for the dev session, per system, kept across page reloads (not across application restarts unless stored by the browser).
Out of scope: application calls (never affected); the supervisor's own planning model call (inside the planner, not an agent).

## Shared building block (research 2026-10-18)
No public interception API exists (AgentListener callbacks are void, no veto or around hook, also in the 1.23 snapshot). One internal mechanism serves DRAFT-3, 4 and 5:
- Extend the PlannerDecisions decorator to the built-in planners too: in a Dev UI run (listeners.owner(scope) at init), rewrite each AgentExecutor of an Action.AgentCallAction to new AgentExecutor(new DelegatingInvoker(original invoker), agent). This single point covers AI, non-AI, nested workflows, A2A/MCP, supervisor and pattern choices and parallel mapper items (which the sub-agent list swap of HumanInTheLoopSteps misses: ParallelMapperPlanner builds MapperAgentInvoker executors itself). Agent ids are kept, so node correlation is unchanged.
- The invoker only has to return a value of the agent's output type: AgentExecutor writes it under the output key, records the invocation and notifies the planner; async agents are wrapped automatically. It must fire before/after listener events itself, as the human-in-the-loop DevUiStep does.
- New internal names go in LangChain4jInternals; guarded so an incompatible langchain4j only disables the feature.
- Upstream ask to record (next to U3-U5): an around-invocation hook on AgentListener returning a replacement output, and a public scope-seeding entry point.

## Specific risks
- A "skip" must return the current value (scope.readState(outputKey)): writing null removes the key.
- Pinning a workflow node skips its inner agents and their scope keys: only safe when the past run recorded those keys (run events record the agents' output keys today, not other writeState calls: prerequisite to decide).
- A pinned AI agent still gets asked for its last user message by the scope context (DefaultAgenticScope.registerContext): stale or null context for summarising agents and supervisors.
- Typed values (records, generics, enums) from JSON; mapper items (pin all items or per index?).

## Open questions
- Allow pinning workflow nodes in a first version, or leaf agents only?
- Where pins are stored (browser storage vs RunManager, and survival across hot reload).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A leaf agent (AI or not) can be pinned from a past run's output or a typed JSON value, and unpinned
- [ ] #2 In Dev UI runs a pinned agent makes no model call: its pinned value is written under its output key, downstream agents, @Output methods and loop exit conditions see it
- [ ] #3 Pinned nodes are marked on the graph and in the timeline (no tokens, no duration)
- [ ] #4 Application calls of the same system are never affected
- [ ] #5 Pinning works for agents inside parallel workflows, loops, conditionals, supervisors and parallel mappers
- [ ] #6 If the internal hook is unavailable the pin controls are disabled with a message; tests cover a sequence, a loop, a parallel mapper and a supervisor
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it needs internal langchain4j hooks (no public API yet). Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
