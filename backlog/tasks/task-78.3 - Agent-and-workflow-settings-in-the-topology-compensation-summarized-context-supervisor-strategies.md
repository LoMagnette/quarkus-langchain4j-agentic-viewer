---
id: TASK-78.3
title: >-
  Agent and workflow settings in the topology: compensation, summarized context,
  supervisor strategies
status: To Do
assignee: []
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 08:27'
labels:
  - review
  - deployment
  - runtime
  - runtime-dev
  - dev-ui
dependencies: []
parent_task_id: TASK-78
priority: medium
type: feature
ordinal: 132000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Review finding 3. Settings of the pinned API that change how a system behaves are not kept by the scanner (`AgentDescriptor`) nor by the shared model (`AgentNode`), so the node details describe supervisors, compensation and context summarization incompletely:
- `compensateOnError` on `@Agent` and on every workflow annotation (sequence, loop, parallel, conditional, parallel mapper, planner, supervisor);
- `@Agent.summarizedContext`;
- `@SupervisorAgent.maxAgentsInvocations`, `contextStrategy`, `responseStrategy`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The node details show compensateOnError when set, the summarized context keys of an agent, and for a supervisor its max invocations, context strategy and response strategy
- [ ] #2 Defaults are not shown as noise: a setting left at its default is either hidden or marked as default
- [ ] #3 Builder systems show the same settings where the public AgentInstance API exposes them; what it does not expose is documented
- [ ] #4 Scanner tests cover each setting; a sample uses them; build-time / runtime topology parity tests include the new fields
- [ ] #5 User documentation describes the new details
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Upstream meaning (langchain4j-agentic 1.20.2 sources): compensateOnError = on a tool failure, previous successful tool calls with @CompensateFor actions are compensated in reverse order (on @Agent and every workflow annotation); summarizedContext = names of other agents whose interaction defines this agent's context; supervisor maxAgentsInvocations (default 10), contextStrategy (CHAT_MEMORY default, SUMMARIZATION, CHAT_MEMORY_AND_SUMMARIZATION), responseStrategy (LAST default, SUMMARY, SCORED). AgentInstance exposes none of them.
1. runtime topology: record AgentSettings(compensateOnError, summarizedContext, maxAgentsInvocations, contextStrategy, responseStrategy), AgentNode.settings (null = unknown, e.g. a builder workflow); the old canonical constructor kept as a secondary one.
2. Scanner: AgentDescriptor.settings from @Agent / workflow annotations, supervisor values with the annotation defaults when absent; TopologyBuilder copies them.
3. BuilderTopology: agents built from annotated types take them from the type's annotations (public reflection, as the AI details); builder workflows and supervisors: unknown (private builder state), documented.
4. Node details: 'Compensation: on error' only when on; summarized context as chips; for supervisors max invocations, context and response strategies, each marked '(default)' when it is.
5. Tests: scanner fixtures for each setting, TopologyBuilder copy, builder parity (annotated agent types), UI test of the details; a sample sets the ones that make no extra model call (compensateOnError, maxAgentsInvocations); SUMMARIZATION / SCORED / summarizedContext call the model, so only fixtures use them.
6. Docs: index.adoc node details, CLAUDE.md.
<!-- SECTION:PLAN:END -->
