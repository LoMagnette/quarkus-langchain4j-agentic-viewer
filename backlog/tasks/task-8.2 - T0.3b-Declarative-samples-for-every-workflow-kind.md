---
id: TASK-8.2
title: 'T0.3b: Declarative samples for every workflow kind'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 09:01'
labels:
  - samples
milestone: m-0
dependencies:
  - TASK-8.1
  - TASK-7.2
documentation:
  - spec-breakdown.md
parent_task_id: TASK-8
priority: high
type: task
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.3. Fixtures for discovery (Phase 1) and live view (Phase 2). Includes the edge cases later tasks test against: a loop of 3 iterations (T2.8), a failing agent with `@ErrorHandler` (T2.10), a HITL step (T2.11), the bank supervisor (T3.1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Samples exist for sequence, parallel, parallel mapper, loop (3 iterations), conditional with @ActivationCondition, nested loop-in-sequence
- [x] #2 Samples exist for a supervisor (bank: exchange, withdraw, credit), a custom planner, @HumanInTheLoop and an agent failing with an @ErrorHandler
- [x] #3 At least one AI agent uses tools, @SystemMessage/@UserMessage and @Output
- [x] #4 Each sample has a test that runs it with the fake model and asserts its output
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
New package integration-tests ...viewer.it.samples, one file per sample: root interface, nested sub-agents, and a nested ChatModelScript bean holding its scripted replies (prompts are distinct per agent so scripts never overlap).
1. StoryWriter: sequence + @Output; AI agents with @SystemMessage/@UserMessage.
2. ResearchGatherer: parallel + @Output.
3. PhraseTranslator: @ParallelMapperAgent over a list of phrases.
4. StoryCreator: sequence containing StyleReviewLoop (@LoopAgent + @ExitCondition, exits after exactly 3 iterations); covers loop and nested loop-in-sequence.
5. ExpertRouter: sequence {CategoryRouter, @ConditionalAgent with @ActivationCondition per expert}.
6. BankSupervisor: @SupervisorAgent over exchange, withdraw, credit agents using @ToolBox CDI tools (BankTool, ExchangeTool).
7. DraftReviewer: @PlannerAgent with a custom Planner (@PlannerSupplier): draft, review, redraft until approved.
8. TripPlanner: sequence with a @HumanInTheLoop step; answers come from a pluggable HumanInput (placeholder until the Dev UI can answer, T2.11/U4).
9. ResilientStoryWriter: @ErrorHandler retrying with a default topic when the topic argument is missing.
SampleWorkflows injects every root (needed by the build-time validation). Old prototype samples (SampleAgents, ParallelAgentExample, DeclarativeWorkflowExample, SampleTools) removed; MixedPatternExample (deferred builder) points at StoryWriter sub-agents.
Tests: DeclarativeSamplesTest (@QuarkusTest), one test per sample asserting the output and, where relevant, the model calls (3 loop iterations, supervisor order, planner steps, HITL prompt).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Samples (integration-tests, package ...viewer.it.samples), each with its scripted replies in a nested ChatModelScript bean:
StoryWriter (sequence + @Output, @SystemMessage/@UserMessage), ResearchGatherer (parallel + @Output), PhraseTranslator (@ParallelMapperAgent), StoryCreator (sequence containing StyleReviewLoop: @LoopAgent + @ExitCondition, exactly 3 iterations), ExpertRouter (sequence + @ConditionalAgent with @ActivationCondition), BankSupervisor (@SupervisorAgent over exchange/withdraw/credit agents using @ToolBox CDI tools), DraftReviewer (@PlannerAgent + @PlannerSupplier custom planner), TripPlanner (@HumanInTheLoop, answers from HumanInput), ResilientStoryWriter (@ErrorHandler retry with a default topic).
SampleWorkflows injects all 9 roots (needed by build-time validation). Old prototype samples removed; MixedPatternExample (deferred builder) now uses StoryWriter sub-agents.

Validation: mvn install of integration-tests, 22 tests pass; DeclarativeSamplesTest has one test per sample asserting the exact output and the model calls (3 loop iterations, conditional calls only the matching expert, supervisor calls exchange/withdraw/credit in order and balances end at 885/1115, planner does draft/review/draft/review, HITL prompt asked, error handler recovers a null topic). Reran 3 times: no failures. Dev mode starts with the new samples (prototype scanner: 31 agents, 3 tools, 8 compositions).

AC notes: AC1 loop and nested loop-in-sequence are one sample (StoryCreator). AC3: the bank agents use tools and @SystemMessage/@UserMessage; @Output is a workflow feature, used by StoryWriter and ResearchGatherer.

Script-writing lessons (documented on ChatModelScript and ScriptedChatModel.userMessageAfter): langchain4j appends output format instructions to the user message for non-String return types (enum values, numbers), so match the prompt line, not the whole message; tool names are not unique across samples, so tool-result rules must also identify the agent; order supervisor rules most specific first (a later response can contain an earlier one's text).

HumanInput is a placeholder answer source (default: approve) until the Dev UI can answer pending human steps (T2.11 / U4).

Finding: quarkus-langchain4j-agentic 1.14.1 ships its own Dev UI (AgenticDevUIProcessor): pages Agents, Topology, Executions, Testing, a JSON-RPC service and DevAgentMonitorHolder. On this sample app the Agents page lists all 33 agents with type, root flag, description, output key and sub-agents; Topology showed "No root agents detected" and Testing an empty agent selector (with a JS error) before any run. Overlaps the spec's F1-F4; raised with the project owner.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added 9 declarative samples covering sequence, parallel, parallel mapper, loop nested in a sequence (3 iterations), conditional, supervisor with tools (bank), custom planner, human in the loop and error handler, each scripted on the fake model and tested end to end (DeclarativeSamplesTest, 9 tests; 22 tests in the module, stable over 3 reruns). Dev mode starts with them.
<!-- SECTION:FINAL_SUMMARY:END -->
