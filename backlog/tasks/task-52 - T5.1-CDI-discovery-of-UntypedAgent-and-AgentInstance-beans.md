---
id: TASK-52
title: 'T5.1: CDI discovery of UntypedAgent and AgentInstance beans'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-17 06:26'
labels:
  - runtime-dev
  - builder
  - discovery
  - deferred
milestone: m-5
dependencies:
  - TASK-11
  - TASK-28
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.1 (F1.3.1, the default `cdi` strategy of `quarkus.langchain4j.agentic.dev-ui.builder-discovery`). Builder systems only exist at runtime; the cheapest way to find them is to inspect CDI beans whose type is `UntypedAgent` or implements `AgentInstance`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 At startup in dev mode, beans of type UntypedAgent or implementing AgentInstance are registered in AgenticSystemRegistry with origin BUILDER
- [x] #2 Produced builder systems from the samples appear in listSystems
- [x] #3 Beans that are sub-agents of another discovered system are not listed as roots
- [x] #4 Discovery does not eagerly create beans with side effects beyond what the app already does
- [x] #5 quarkus.langchain4j.agentic.dev-ui.builder-discovery exists with cdi as default, documented and tested
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Build config builder-discovery (cdi default, none).
2. Build time (dev only): BuilderSystemBeans picks producer beans typed as agents (UntypedAgent, SupervisorAgent, AgentInstance) or whose producer body calls an agentic build() (ASM), keeps them unremovable, records their bean ids (doc-6 recommendation).
3. Runtime: BuilderSystems creates them on the first listing (registry loads lazily; startup uses build-time systems), keeps those without a parent, topology via BuilderTopology. Run disabled / refused until T5.6.
4. Tests: dev-mode fixture (non-AI builder systems) for listing, laziness, sub-agent exclusion and none; samples checked in dev mode; docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Verification: BuilderDiscoveryDevModeTest (pipeline and Greeter listed, cleanup sub-agent not, no producer called before listSystems, topology served) and BuilderDiscoveryDisabledDevModeTest (none: nothing listed or created). Sample app in dev mode (scratch copy, port 8091): listSystems shows builderStoryCreator and BuilderStoryWriter (origin BUILDER) next to the 10 declarative systems, not the builderStyleReviewLoop bean; getTopology gives the StoryCreator shape with a 'lambda' loop guard; startRun refused; headless Chromium: graph renders, Run disabled with its reason, no page error. Full clean builds: default deployment 120 + integration 88, -Ppatterns deployment 124 (1 skipped) + integration 107.
Finding: an agent has one parent; builder systems given the agent beans of declarative systems broke them (ListenerHookSpikeTest). Samples and tests now build their own agents (agentBuilder(Interface).chatModel), regression test in BuilderSamplesTest; documented (docs TIP, CLAUDE.md). Card label still counts the build-time systems only.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Builder systems produced as CDI beans are now listed in the Dev UI with their graph: the build-time step picks producer beans typed as agents or calling an agentic build() and records their ids; runtime-dev creates them on the first listing and drops sub-agents. New build config builder-discovery (cdi default, none), documented. Verified with two dev-mode tests, the sample app in dev mode plus headless Chromium, and full builds in both configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
