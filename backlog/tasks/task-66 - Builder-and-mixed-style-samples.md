---
id: TASK-66
title: Builder and mixed-style samples
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:26'
updated_date: '2026-10-17 05:57'
labels:
  - samples
  - builder
milestone: m-5
dependencies:
  - TASK-8.1
  - TASK-7.2
documentation:
  - spec-breakdown.md
priority: low
type: task
ordinal: 101000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.3 (builder part). Moved out of T0.3 (was TASK-8.3) when builder support was deferred (decision-2).

Fixtures for builder discovery (Phase 5) and its spikes (T0.5, T0.6). Covers every discovery strategy: CDI-produced systems, systems not exposed as beans, systems rebuilt per request, and mixes of builder and declarative.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A builder system produced as a CDI bean (UntypedAgent) and one exposed as a typed agent interface exist
- [x] #2 A builder system that is not a bean and one rebuilt per request exist
- [x] #3 A builder workflow using declarative agent classes as sub-agents, and a declarative workflow using a builder sub-agent, exist
- [x] #4 A builder twin of one declarative sample exists for topology comparison (T5.2)
- [x] #5 Each sample has a test that runs it with the fake model
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Builder samples reuse the declarative samples' agent interfaces and scripts (untyped agentBuilder agents ignore their prompt under Quarkus).
2. UntypedAgent bean: builder twin of StoryCreator. Typed interface bean: sequenceBuilder(BuilderStoryWriter.class) over StoryWriter agents. Not a bean: system in a service field. Per request: REST resource rebuilding a conditional system. Mixed: declarative BlurbedStory whose non-AI step delegates to the builder system (builder workflows over declarative agents are covered by every builder sample).
3. BuilderSamplesTest runs each on the fake model; remove the unused MixedPatternExample.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding from TASK-7.2 (quarkus-langchain4j-agentic 1.14.1 build-time validation): output keys of non-AI agents (classes with @Agent methods) are only known when the class is listed in a declarative subAgents, and parameters are only user-provided when the root interface is injected. A builder-only chain of non-AI agents therefore fails the Quarkus build. Builder samples must use AI agent interfaces whose keys are resolvable, or reuse declarative agents (as MixedPatternExample does).

Finding from TASK-8.1: under Quarkus, programmatic untyped agents (AgenticServices.agentBuilder().userMessage(...)) ignore the userMessage template; the user message becomes the argument map, because Quarkus generates AI service implementations at build time from annotated interfaces. Builder samples should use annotated agent interfaces.

Findings for the spikes and Phase 5:
- A builder given agent interfaces as sub-agents (subAgents(X.class)) builds them itself, without the application's chat model: AgenticSystemConfigurationException. Quarkus apps must pass the agent beans, unwrapped from their client proxies (langchain4j does not recognise a ClientProxy as an agent: 'No agent method found in class ..._ClientProxy'). Helper it.builder.AgentBeans.
- quarkus-langchain4j-agentic resolves non-AI agent classes of declarative workflows from CDI (QuarkusSubAgentResolver); the class must be a bean and @Unremovable (otherwise removed, and the fallback tries to build it as an AI service). Verified by removing it: the mixed test fails.
- A typed builder interface needs no agentic annotation (any method writes its @V parameters to the scope), so it is no declarative agent, but its root AgentInstance then declares no arguments (matters for the run form, T5.6).
- BlurbedStory: the builder system runs as a separate root invocation with its own scope.
Validation: BuilderSamplesTest 5/5; integration-tests clean verify default 74 tests + ProdArtifactIT, -Ppatterns 93 + ProdArtifactIT (native IT skipped). Runs done in a scratch copy of the repo, since a dev mode on the host uses integration-tests/target.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added builder and mixed-style samples (it.builder): an UntypedAgent bean twin of StoryCreator, a typed interface bean, a system in a service field, one rebuilt per REST request, and a declarative workflow delegating to a builder system; each runs on the fake model in BuilderSamplesTest. Recorded the Quarkus rules for builder systems (agent beans unwrapped from proxies, unremovable non-AI agent classes). Verified with clean integration-test runs in both configurations (74 and 93 tests).
<!-- SECTION:FINAL_SUMMARY:END -->
