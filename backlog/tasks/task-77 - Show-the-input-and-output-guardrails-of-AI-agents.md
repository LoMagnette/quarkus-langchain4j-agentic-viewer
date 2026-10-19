---
id: TASK-77
title: Show the input and output guardrails of AI agents
status: To Do
assignee: []
created_date: '2026-10-18 13:45'
labels:
  - dev-ui
  - deployment
dependencies: []
priority: medium
type: feature
ordinal: 128000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
An AI agent can carry langchain4j input and output guardrails (`@InputGuardrails` / `@OutputGuardrails` of `dev.langchain4j.service.guardrail`, on the agent interface or method; output guardrails also have `maxRetries`). The viewer shows none of them today: nothing reads these annotations, so a developer cannot tell from the graph or the node details which checks run around an agent call, nor why a run failed on one.

## Scope
- Before running: list the guardrails of each AI agent (method level and class level, as langchain4j resolves them) in the node details, with a marker on the graph node. Declarative systems through the build-time scan; builder systems from the annotations of the agent type (public `AgentInstance.type()`).
- During a run: a guardrail failure (`InputGuardrailException` / `OutputGuardrailException`) is told apart from other agent errors in the timeline.

## Out of scope
- Guardrails set in code on `AgentBuilder` (`inputGuardrails(...)`, `outputGuardrailClasses(...)`, ...): held in private fields, reading them needs internal access.
- The result of each guardrail and the output guardrail retries during a run: langchain4j reports them as `GuardrailExecutedEvent`s to `AiServices` listeners only, which agents do not expose publicly. Postponed with the other internal hooks (no new internal langchain4j access for now).

## To check first
That quarkus-langchain4j-agentic applies these annotations to agents (langchain4j `AgentBuilder` hands guardrails to `AiServices`), and whether the deprecated `io.quarkiverse.langchain4j.guardrails` annotations still matter.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The node details of an AI agent list its input guardrails and output guardrails (class names, in declaration order) and the output maxRetries when set; method-level annotations take precedence over class-level ones as in langchain4j
- [ ] #2 An AI agent node with guardrails has a visible marker on the graph; agents without guardrails are unchanged
- [ ] #3 Builder systems show the guardrails declared by annotations on their agent types
- [ ] #4 A run failing on an input or output guardrail shows the failure as a guardrail failure (which guardrail kind, its message) in the timeline and node info
- [ ] #5 Topology parity between build time and runtime (BuilderTopology) is kept for the new fields
- [ ] #6 Tests: scanner test for class and method annotations, a sample agent with guardrails in integration-tests, a run failing on a guardrail, and a UI test of the node details section
- [ ] #7 User documentation (index.adoc) describes what is shown and what is not (builder-set guardrails, per-guardrail results)
<!-- AC:END -->
