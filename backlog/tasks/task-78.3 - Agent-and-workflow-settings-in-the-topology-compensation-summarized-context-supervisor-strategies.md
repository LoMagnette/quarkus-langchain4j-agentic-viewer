---
id: TASK-78.3
title: >-
  Agent and workflow settings in the topology: compensation, summarized context,
  supervisor strategies
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 09:01'
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
- [x] #1 The node details show compensateOnError when set, the summarized context keys of an agent, and for a supervisor its max invocations, context strategy and response strategy
- [x] #2 Defaults are not shown as noise: a setting left at its default is either hidden or marked as default
- [x] #3 Builder systems show the same settings where the public AgentInstance API exposes them; what it does not expose is documented
- [x] #4 Scanner tests cover each setting; a sample uses them; build-time / runtime topology parity tests include the new fields
- [x] #5 User documentation describes the new details
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented as planned. AgentSettings record, AgentNode.settings (new canonical constructor, the previous one kept), AgentDescriptor.settings; supervisor values default to 10 / CHAT_MEMORY / LAST like @SupervisorAgent. BuilderTopology reads them from the @Agent or declarative workflow annotation of the agent type (annotation attributes through public reflection); a workflow made by a builder has null (unknown). Node details: Compensation and Summarized context only when set; supervisors show max invocations, context and response strategies with '(default)'. StoryCreator.Drafter now has compensateOnError = true (no tool, no effect on runs) so the builder parity check covers a non-default value; BankSupervisor already uses responseStrategy SUMMARY. Validation: mvn -o clean install (deployment 175, integration-tests 106, ITs 6 / 1 skipped), mvn -o clean install -Ppatterns (184 / 1 skipped, 139, 6 / 1 skipped), UI tests 29/29 (new agentSettingsAreShownAndSupervisorDefaultsMarked). New AgentSettingsScanTest; BuilderTopologyTest graph() compares leaf settings.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Agent and workflow settings that change behaviour are now part of the topology: compensateOnError (every annotation), summarizedContext, and the supervisor's max invocations, context strategy and response strategy (annotation defaults applied). The node details show compensation and summarized context when set and the supervisor strategies marked when default. Builder systems take them from their agent types' annotations; workflows built in code leave them unknown (documented). Verified with both CI builds and the 29 UI tests.
<!-- SECTION:FINAL_SUMMARY:END -->
