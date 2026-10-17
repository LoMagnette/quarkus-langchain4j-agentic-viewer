---
id: TASK-53
title: 'T5.2: Runtime TopologyBuilder from an AgentInstance tree'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-17 06:07'
labels:
  - runtime-dev
  - builder
  - topology
  - deferred
milestone: m-5
dependencies:
  - TASK-10
  - TASK-20
  - TASK-66
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.2. Builds on the T0.5 spike. Builder systems must produce the same topology model as declarative ones, so the graph and live view work unchanged. Builder guards are lambdas and can only be labelled "lambda".
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 TopologyBuilder converts a live AgentInstance tree into an AgenticSystem
- [x] #2 The builder twin of a declarative sample produces the same graph as the declarative system (except guard labels)
- [x] #3 Lambda guards are labelled "lambda"
- [x] #4 Unit tests cover every node kind built with the builder API
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime-dev builder/BuilderTopology: AgenticSystem from a live AgentInstance tree through the public API (doc-5), node ids with the run-event rule (simple type name, ~n).
2. Kind: planner class first (SEQUENCE topology for supervisors/planners), then topology and annotations (human step, A2A, MCP). Workflow input keys computed from their agents when none declared; root inputs typed from the agents' arguments.
3. Guards: loop exit and branch conditions from LoopAgentInstance/ConditionalAgentInstance, lambda without description -> "lambda". AI details from the annotations of type(), as the build-time scanner.
4. Tests (integration-tests BuilderTopologyTest): twin vs declarative graph, ids, lambda labels, every builder node kind, MIXED origin.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Implemented builder/BuilderTopology; planner class names cataloged in LangChain4jInternals (LangChain4jInternalsTest passes).
Verification: BuilderTopologyTest 6/6 (builder twin graph == build-time StoryCreator graph except guard labels; the declarative StoryCreator read at runtime gives the same graph; lambda -> "lambda", described condition kept; every builder kind: SEQUENCE, PARALLEL, PARALLEL_MAPPER, LOOP, CONDITIONAL, SUPERVISOR, PLANNER (CUSTOM), HUMAN_IN_THE_LOOP, NON_AI_AGENT (agentAction), AI_AGENT with prompts; MIXED via a stub tree). Full integration-tests clean verify: 87 tests green.
Not covered: A2A builder agents (a2aBuilder needs langchain4j-agentic-a2a and a server; such an agent would show as NON_AI_AGENT since its type carries no annotation). Limits: a builder parallel mapper's items key is private to its planner (input keys are its agent's, e.g. phrase instead of phrases); a Quarkus declarative workflow bean cannot be a builder sub-agent (Agent executor not found), so MIXED is rare in practice.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added BuilderTopology (runtime-dev), which turns a live AgentInstance tree into the shared topology model with the run-event node id rule, lambda guards labelled "lambda" and AI details read from the agent interfaces. Verified with BuilderTopologyTest (6 tests): the builder twin and the runtime-read declarative StoryCreator both match the build-time graph (guard labels aside), and every builder node kind maps right; A2A builder agents are not covered (module and server needed).
<!-- SECTION:FINAL_SUMMARY:END -->
