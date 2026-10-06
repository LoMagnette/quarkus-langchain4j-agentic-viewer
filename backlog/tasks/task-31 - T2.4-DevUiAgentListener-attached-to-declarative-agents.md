---
id: TASK-31
title: 'T2.4: DevUiAgentListener attached to declarative agents'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
updated_date: '2026-10-06 09:36'
labels:
  - runtime-dev
  - live-view
milestone: m-2
dependencies:
  - TASK-9
  - TASK-27
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.4 (F6.2). Depends on the hook found by the T0.4 spike, or on U5 if the spike found none. The listener is an `AgentListener` with `inheritedBySubagents() = true` that maps each callback to an execution event. Listener callbacks run on agent threads, so they must never block.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The listener is attached to every declarative root system and inherited by sub-agents
- [ ] #2 All callbacks (scope created/destroyed, agent before/after/error, tool before/after) are received and mapped to events for every sample system
- [ ] #3 Callbacks only enqueue the event and return; a test with a slow subscriber shows no delay in agent execution
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design input from TASK-9 (doc-3): register the Dev UI listener on each root agent instance at dev-mode startup (InternalAgent.registerInheritedParentListener, inheritedBySubagents = true); do not make it a CDI AgentListener bean (duplicate AI-agent callbacks). Re-register after hot reload.
<!-- SECTION:NOTES:END -->
