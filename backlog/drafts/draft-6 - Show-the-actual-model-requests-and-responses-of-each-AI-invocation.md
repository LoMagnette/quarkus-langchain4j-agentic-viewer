---
id: DRAFT-6
title: Show the actual model requests and responses of each AI invocation
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 06:13'
labels:
  - idea
  - dev-ui
  - observability
  - postponed
dependencies: []
priority: low
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
The timeline shows what an agent got (its inputs) and gave (its output), not what the model received. Prompt problems (a template variable rendered empty, a missing tool, a system message overridden, a model name override) are invisible: the developer has to add logging in the application. Showing the actual model exchange of each AI invocation answers "why did the model do that?".

## Scope
For each AI agent invocation of a Dev UI run, in the timeline (row details) and node details:
- the rendered system and user messages, the tool specifications sent, the model name and parameters (temperature, response format...);
- each model round: the AI message (text, tool execution requests), the tool results fed back, token usage and finish reason per round;
- bounded like every payload (PayloadTruncator, max-payload-size).
Out of scope: raw HTTP bodies (not available in general), application calls (Dev UI runs only), editing and replaying a request (later).

## Feasibility (research 2026-10-18)
- Step 1, public API, cheap: AgentResponse.chatRequest()/chatResponse() give the last round (already used for token usage).
- Step 2, per round: an AiServiceListener (AiServiceStartedEvent: rendered messages; AiServiceResponseReceivedEvent: request and response per round, tool rounds included; ToolExecutedEvent) registered on each agent's AiServiceContext.eventListenerRegistrar. Correlation by invocationContext().managedParameters().get(AgenticScope.class), on any thread, as langchain4j-agentic itself does (AgentInvocationHandler).
- quarkus-langchain4j only turns these events into CDI events for @RegisterAiService beans, not for agentic agents: reaching the registrar needs internal API (Quarkus agent `agent` field, already listed, then the private AgentInvocationHandler.context). Per-scope agent copies get a new context: register lazily and idempotently from beforeAgentInvocation. To be listed in LangChain4jInternals and guarded like the other hooks.
- Rejected: ChatModelListener (misses models the application produces itself, e.g. the scripted fake; streaming responses arrive on HTTP threads where nothing correlates).

## Open questions
- Accept one more internal hook, or first ask upstream for AiServiceListener support on agentic agents (new upstream item)?
- How much of the per-round history to keep in run history (payload size x history-size)?
- Show it inline in the timeline, in node details, or a dedicated "Model" tab?

## Related
Needed by DRAFT-10 (run to test: recorded model replies); shares the per-agent context hook with DRAFT-7 (live tokens).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The details of an AI agent invocation in a Dev UI run show the rendered system and user messages, the tool specifications, the model name and parameters
- [ ] #2 Each model round of the invocation (tool-call rounds included) shows the AI message, the tool results fed back and its token usage
- [ ] #3 Works for streaming and non-streaming agents and for models produced by the application (e.g. the scripted fake)
- [ ] #4 Payloads are bounded by max-payload-size; application calls are not observed
- [ ] #5 If the internal hook is unavailable (incompatible langchain4j), the last round from the public AgentResponse is still shown and dev mode logs the missing part (LangChain4jInternals)
- [ ] #6 Tests cover a single round, a tool round and the degraded mode
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it needs internal langchain4j hooks (no public API yet). Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
