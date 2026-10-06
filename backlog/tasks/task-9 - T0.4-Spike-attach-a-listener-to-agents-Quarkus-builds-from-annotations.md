---
id: TASK-9
title: 'T0.4: Spike - attach a listener to agents Quarkus builds from annotations'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 09:22'
labels:
  - spike
  - runtime-dev
milestone: m-0
dependencies:
  - TASK-8
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.4. Answers the open question "Does quarkus-langchain4j-agentic already expose a way to customise the builders it uses for annotated agents?".

This is the top risk in the spec: without a hook to add an `AgentListener` to agents Quarkus creates from annotations, the whole live view is blocked for declarative systems. Fallbacks are an upstream change (U5) or a dev-mode bytecode transform.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A written finding documents whether an existing hook exists and how it works, or why none exists
- [ ] #2 A prototype receives beforeAgentInvocation for every agent of a nested declarative sequence
- [ ] #3 If no hook exists, the finding recommends U5 or the bytecode-transform fallback with its cost
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-67 (doc-1): quarkus-langchain4j-agentic 1.14.1 AgenticProcessor wires every CDI AgentListener bean into all agents it builds ("AgentListener CDI beans are additive — wired into all agents"). This is likely the hook this spike looks for; the prototype still has to confirm callbacks for a nested sequence.
<!-- SECTION:NOTES:END -->
