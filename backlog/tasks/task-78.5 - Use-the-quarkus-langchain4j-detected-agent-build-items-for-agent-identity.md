---
id: TASK-78.5
title: Use the quarkus-langchain4j detected agent build items for agent identity
status: To Do
assignee: []
created_date: '2026-10-19 07:45'
labels:
  - review
  - deployment
dependencies: []
parent_task_id: TASK-78
priority: high
type: enhancement
ordinal: 134000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Review finding 2. quarkus-langchain4j-agentic 1.14.2 (already a dependency of the deployment module) produces `DetectedAiAgentBuildItem` (interface, agent methods, chat model supplier, model name, toolbox, MCP toolbox and skills methods), `DetectedAiAgentAsMapBuildItem` and `DetectedNonAiAgentAsMapBuildItem` from its own annotation resolution, the one that builds the agents. The viewer rescans the `CombinedIndexBuildItem` on its own (`AgenticAnnotationScanner`), so two interpretations of the same application exist: one builds the agents, the other draws them. Parity tests reduce the risk of drift but cannot prevent it when upstream changes its rules.

## Direction (from the review)
Take agent identity and the AI agent metadata from the upstream build items; keep a supplemental Jandex scan only for what they do not expose (workflow structure, keys, guards, prompts...); propose a richer upstream build item if workflow metadata is missing. The detailed approach is decided when the task starts and is reviewed with the owner before implementation (architecture change).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The set of AI agents and non-AI agents shown comes from the upstream detected agent build items; an agent upstream does not detect is not shown as an agent, and the reverse
- [ ] #2 AI agent metadata the upstream items expose (model name, chat model supplier, toolbox, MCP toolbox, skills) is taken from them rather than rescanned
- [ ] #3 What remains scanned by the viewer is listed in CLAUDE.md with the reason (not exposed upstream)
- [ ] #4 Existing scanner, topology and sample tests pass unchanged in both CI configurations; a test shows the viewer and quarkus-langchain4j-agentic agree on the agents of the sample app
- [ ] #5 A Backlog document records what an upstream build item would need to expose to drop the supplemental scan, as a proposal for quarkus-langchain4j
<!-- AC:END -->
