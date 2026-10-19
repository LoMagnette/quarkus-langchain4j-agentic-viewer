---
id: TASK-78.5
title: Use the quarkus-langchain4j detected agent build items for agent identity
status: To Do
assignee: []
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 09:17'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Proposed, to review with the owner before implementation (architecture change).
Findings (quarkus-langchain4j-agentic 1.14.2 AgenticProcessor.detectAgents): for each of its agent annotations (@Agent, @HumanInTheLoop, @A2AClientAgent, the seven workflow annotations, @McpClientAgent; not @RegistryAgent), an annotated method of an interface puts the interface and its sub-interfaces in DetectedAiAgentAsMapBuildItem, of a class puts the class in DetectedNonAiAgentAsMapBuildItem; DetectedAiAgentBuildItem (one per interface, dev.langchain4j.agentic.* left out) adds the @ChatModelSupplier method, the model name, and the @ToolBox / @McpToolBox / @Skills methods. Nothing about structure: sub-agents, keys, conditions, @Output, planners, prompts.
1. A dev-only step turns the three upstream items into one viewer build item (agent methods by class, with the AI metadata), consumed by the scan step.
2. The scanner takes that selection as an optional input: with it, it describes exactly the agent methods upstream detected (identity), and takes model name, chat model supplier, toolbox / MCP toolbox / skills methods from the upstream item; it keeps reading with Jandex what upstream does not expose (structure, keys, guards, prompts, tool methods of the toolbox classes, A2A/MCP details, suppliers, settings). Without it (unit tests calling the scanner on Index.of), it keeps its own selection.
3. @RegistryAgent stays a supplemental scan (upstream 1.14.2 does not detect it), listed as such.
4. Drift check: in dev mode, the viewer logs once the agent methods its own rules and upstream disagree on; a dev-mode test checks none for the fixture app, and an integration test compares both selections on the sample app (they must agree).
5. CLAUDE.md lists what is still scanned and why; a Backlog document drafts the upstream proposal: a build item exposing the resolved agent tree (sub-agents, output key, typed keys, conditions, @Output, planner supplier, human-in-the-loop and remote details) so the supplemental scan can go.
Alternative, smaller: keep the scanner as the source and only add the drift check of step 4 (no consumption of the upstream items).
<!-- SECTION:PLAN:END -->
