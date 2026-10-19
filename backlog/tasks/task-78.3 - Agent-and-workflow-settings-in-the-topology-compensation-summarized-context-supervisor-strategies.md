---
id: TASK-78.3
title: >-
  Agent and workflow settings in the topology: compensation, summarized context,
  supervisor strategies
status: To Do
assignee: []
created_date: '2026-10-19 07:45'
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
