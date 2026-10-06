---
id: TASK-66
title: Builder and mixed-style samples
status: To Do
assignee: []
created_date: '2026-10-06 06:26'
updated_date: '2026-10-06 08:41'
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
- [ ] #1 A builder system produced as a CDI bean (UntypedAgent) and one exposed as a typed agent interface exist
- [ ] #2 A builder system that is not a bean and one rebuilt per request exist
- [ ] #3 A builder workflow using declarative agent classes as sub-agents, and a declarative workflow using a builder sub-agent, exist
- [ ] #4 A builder twin of one declarative sample exists for topology comparison (T5.2)
- [ ] #5 Each sample has a test that runs it with the fake model
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding from TASK-7.2 (quarkus-langchain4j-agentic 1.14.1 build-time validation): output keys of non-AI agents (classes with @Agent methods) are only known when the class is listed in a declarative subAgents, and parameters are only user-provided when the root interface is injected. A builder-only chain of non-AI agents therefore fails the Quarkus build. Builder samples must use AI agent interfaces whose keys are resolvable, or reuse declarative agents (as MixedPatternExample does).

Finding from TASK-8.1: under Quarkus, programmatic untyped agents (AgenticServices.agentBuilder().userMessage(...)) ignore the userMessage template; the user message becomes the argument map, because Quarkus generates AI service implementations at build time from annotated interfaces. Builder samples should use annotated agent interfaces.
<!-- SECTION:NOTES:END -->
