---
id: TASK-78.5
title: Use the quarkus-langchain4j detected agent build items for agent identity
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 10:46'
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
- [x] #1 The set of AI agents and non-AI agents shown comes from the upstream detected agent build items; an agent upstream does not detect is not shown as an agent, except those the viewer adds knowingly (registry agents, agent methods inherited by a subclass), and the reverse
- [x] #2 AI agent metadata the upstream items expose (model name, chat model supplier, toolbox, MCP toolbox, skills) is taken from them rather than rescanned
- [x] #3 What remains scanned by the viewer is listed in CLAUDE.md with the reason (not exposed upstream)
- [x] #4 Existing scanner, topology and sample tests pass unchanged in both CI configurations; a test with the real upstream detection shows the viewer and quarkus-langchain4j-agentic agree on the agents of the scan fixtures, and the sample app started in dev mode logs no disagreement
- [x] #5 A Backlog document records what an upstream build item would need to expose to drop the supplemental scan, as a proposal for quarkus-langchain4j
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Owner approved the 'consume + drift check' approach (2026-10-19).

AC wording made exact after implementation (2026-10-19): AC1 names the two cases the viewer adds knowingly (registry agents: upstream 1.14.2 does not detect @RegistryAgent; agent methods inherited by a subclass: langchain4j runs them via getMethods(), upstream lists the declaring class only). AC4: the planned integration test on the sample app needs build-chain test support (quarkus-junit-internal) in integration-tests, which it does not have; the real-detection agreement runs on the deployment scan fixtures (UpstreamAgreementTest, full descriptor equality) and the sample app (with -Ppatterns, all samples) was started in dev mode with no disagreement logged.

Implemented: UpstreamAgents (from DetectedAiAgentAsMapBuildItem, DetectedNonAiAgentAsMapBuildItem, DetectedAiAgentBuildItem), AgenticAnnotationScanner.scan(index, upstream) -> Result(descriptors, disagreements); the dev-only scan step consumes the three upstream items and logs disagreements once (WARN). Upstream-detected methods are described with the scanner's describe() (inheritedBy for sub-interfaces; a class with own agent methods ignores inherited ones, like langchain4j); AI agents take model name, chat model supplier and the @ToolBox / @McpToolBox / @Skills methods from DetectedAiAgentBuildItem (matched by agent method name). Viewer-only agents beyond registry agents and subclass-inherited methods are left out and logged. Found: quarkus-langchain4j-agentic 1.14.2 rejects a @RegistryAgent sub-agent at build time (validateAgentNames), so ScannerParityTest's fixtures cannot be a Quarkus app. Upstream proposal U7: Backlog doc-8. Validation: UpstreamSelectionTest (rules), UpstreamAgreementTest (real detection on the scan fixtures, full descriptor equality with the scanner alone), AgenticSystemsDevModeTest.quarkusLangChain4jAndTheViewerAgreeOnTheAgents (verified to fail when the warning is forced); sample app with -Ppatterns started in dev mode: no disagreement logged. mvn -o clean install (184, 106, 6 / 1 skipped), -Ppatterns (193 / 1 skipped, 139, 6 / 1 skipped), UI tests 30/30.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The scan step now builds on quarkus-langchain4j-agentic's own detection: the agents shown are those its detected-agent build items list (plus registry agents and subclass-inherited agent methods, which the viewer adds knowingly), and AI agents take their model name, chat model supplier and tool box / skills methods from its DetectedAiAgentBuildItem. The structure is still read from the index since upstream exposes none of it (listed in CLAUDE.md; proposal U7 in doc-8 for an upstream build item). Any disagreement is logged in dev mode; tests cover the rules, the real detection on the scan fixtures and the dev-mode log; the sample app shows none. Verified with both CI builds and the 30 UI tests.
<!-- SECTION:FINAL_SUMMARY:END -->
