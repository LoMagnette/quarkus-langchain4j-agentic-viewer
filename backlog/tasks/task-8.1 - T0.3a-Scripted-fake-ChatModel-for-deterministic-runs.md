---
id: TASK-8.1
title: 'T0.3a: Scripted fake ChatModel for deterministic runs'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 08:41'
labels:
  - samples
  - testing
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-8
priority: high
type: task
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.3. Tests and samples must not call real models (cost, flakiness, API keys). A scripted fake `ChatModel` returns predefined responses, including tool-call requests, so supervisor, tool and planner flows are reproducible.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A fake ChatModel returns scripted responses selected by agent or prompt
- [x] #2 The fake can script tool-execution requests and supervisor/planner decisions
- [x] #3 Samples and tests use the fake by default, with no API key configured
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ScriptedChatModel (integration-tests, package ...viewer.it.fake): ordered rules (Predicate<ChatRequest> -> reply), first match wins, no unmatched default (fails with the request text so missing scripts are obvious). Stateless on purpose: parallel agents call the model concurrently, so call counters would make results timing-dependent.
2. Matchers on request content: user/system message contains, last message is a tool result (optionally for a given tool). Replies: text, text computed from the request, tool-execution request, supervisor invocation JSON ({"agentName","arguments"}) and supervisor "done". Supervisor steps are chosen statelessly from the "last received response" in the supervisor user message.
3. Records the requests it served, so tests can assert what agents were asked.
4. SampleChatModel CDI producer with the scripts for the samples replaces PlaceholderChatModel; no provider extension, so no API key anywhere.
5. Tests: unit tests for matching/replies, plus end-to-end tests through the real langchain4j-agentic runtime (agent with a tool; supervisor choosing two agents then done) to prove the replies parse.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation (integration-tests, package ...viewer.it.fake):
- ScriptedChatModel: ordered rules, first match wins, stateless (parallel agents call it concurrently); unmatched requests fail with the request content; records served requests. Matchers: userMessageContains, systemMessageContains, supervisorLastResponseContains, lastMessageIsToolResult. Replies: text, computed text, callTool, invokeAgent, finishSupervisor.
- ChatModelScript: CDI beans contribute rules (samples in main, test agents in test). ScriptedChatModelProducer builds the app's only ChatModel from them. PlaceholderChatModel removed.

Validation: mvn clean install, 16 tests pass. ScriptedChatModelTest (5 unit tests). ScriptedChatModelAgenticTest (@QuarkusTest, declarative agents built by quarkus-langchain4j-agentic): an @Agent with a @ToolsSupplier tool executes the scripted tool call and answers from the tool result; a @SupervisorAgent follows the scripted decisions (write, translate, done) and returns the SUMMARY response. Both assert exact replies and request sequences, so they only pass if the fake answered.

AC notes: "by agent" is done through each agent's prompt (agents have distinct @UserMessage/@SystemMessage). LLM-driven planner decisions are the supervisor ones; GOAP/P2P/blackboard planners are algorithmic. Scripts for the samples themselves come with TASK-8.2 (each sample gets a script and a test).

Upstream bug found (quarkus-langchain4j core 1.14.1, AiServicesProcessor): the ChatModel injection point of an AI service context is only declared when a provider extension is selected, but the runtime always requests it. An application ChatModel bean without a provider extension passes the build, then every AI agent fails at creation ("A synthetic injection point was not declared for required type ChatModel"). @ChatModelSupplier on agents hits the same path. Workaround: integration-tests depends on quarkus-langchain4j-openai with quarkus.langchain4j.openai.enable-integration=false (no API key needed, never called); the scripted bean takes precedence. Deployment-test fixtures that invoke agents will need the same. Worth an upstream issue.

Also found: programmatic (untyped) agents built with AgenticServices.agentBuilder() under Quarkus ignore userMessage(...) templates (the user message becomes the argument map), because Quarkus generates AI service implementations at build time. Plain JUnit tests cannot use the agentic builders in this module (they need the Arc container).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added a deterministic scripted fake ChatModel (ScriptedChatModel) with stateless, content-matched rules for text replies, tool calls and supervisor decisions, plus ChatModelScript beans and a producer making it the app's only model. Verified with 5 unit tests and 2 Quarkus tests driving real declarative agents (tool execution, supervisor decisions). Worked around a quarkus-langchain4j 1.14.1 bug (application ChatModel bean not wired without a provider) with a disabled OpenAI provider; no API key needed.
<!-- SECTION:FINAL_SUMMARY:END -->
