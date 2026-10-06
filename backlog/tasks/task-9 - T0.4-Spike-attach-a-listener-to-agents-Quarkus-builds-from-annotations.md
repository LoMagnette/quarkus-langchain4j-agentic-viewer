---
id: TASK-9
title: 'T0.4: Spike - attach a listener to agents Quarkus builds from annotations'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 09:36'
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
- [x] #1 A written finding documents whether an existing hook exists and how it works, or why none exists
- [x] #2 A prototype receives beforeAgentInvocation for every agent of a nested declarative sequence
- [x] #3 If no hook exists, the finding recommends U5 or the bytecode-transform fallback with its cost
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read how quarkus-langchain4j-agentic builds annotated agents (done: AgenticRecorder.QuarkusAgenticContextConsumer adds every CDI AgentListener bean to each agent built through DeclarativeUtil, i.e. AI agents).
2. Prototype in integration-tests (test scope): two recording AgentListener CDI beans, one plain and one with inheritedBySubagents() = true.
3. Run StoryCreator (nested sequence + loop) and, for coverage useful to T2.4/TASK-14, parallel, supervisor with tools, HITL and error-handler samples; record every callback per listener (agent name, kind, thread).
4. Turn the observations into assertions for the nested sequence, and write the finding (what is and is not reported, duplicates, implications for DevUiAgentListener).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-67 (doc-1): quarkus-langchain4j-agentic 1.14.1 AgenticProcessor wires every CDI AgentListener bean into all agents it builds ("AgentListener CDI beans are additive — wired into all agents"). This is likely the hook this spike looks for; the prototype still has to confirm callbacks for a nested sequence.

Finding written in Backlog doc-3. Prototype: ListenerHookSpikeTest (5 tests, integration-tests): CDI AgentListener beans only see AI agents and tools; a listener registered on the root agent via InternalAgent.registerInheritedParentListener sees scope lifecycle, workflow nodes, AI agents, tools and HITL steps; neither sees supervisor decisions nor error-handler retries. Validated with mvn install of integration-tests (27 tests pass). AC 3 does not apply (a hook exists); refinement of U5 recorded in doc-3 and on TASK-16. Pitfall met during the spike: reading a field of an @ApplicationScoped bean through its client proxy returns the proxy's own (empty) field; listener state is now read through methods.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Answered T0.4: quarkus-langchain4j-agentic attaches CDI AgentListener beans to AI agents only, while registering an inherited listener on each root agent instance (InternalAgent.registerInheritedParentListener) reports the whole system, including workflow nodes, scope lifecycle and HITL. Proven by ListenerHookSpikeTest (5 tests) and documented in doc-3 with the recommendation for T2.4.
<!-- SECTION:FINAL_SUMMARY:END -->
