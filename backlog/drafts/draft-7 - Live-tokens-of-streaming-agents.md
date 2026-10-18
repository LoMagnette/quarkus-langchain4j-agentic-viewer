---
id: DRAFT-7
title: Live tokens of streaming agents
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
An agent returning a TokenStream or Multi shows nothing until it finishes: for long generations the run looks stuck, and the developer cannot see the answer forming (nor stop a run going in the wrong direction early).

## Scope
In Dev UI runs, show the tokens of a streaming AI agent as they arrive, in its timeline row and node details, then the final output as today. Never consume, re-subscribe or block the application's stream.
Out of scope: application calls; non-streaming agents.

## Feasibility (research 2026-10-18)
- No public partial-response callback: ChatModelListener has no onPartialResponse in langchain4j 1.20.2; a TokenStream takes one set of handlers and a cold Multi would call the model again if subscribed twice.
- Inner streaming agents run by a planner are wrapped in StreamingResponse (AgentExecutor), which starts the stream with only an onCompleteResponse handler: their tokens are dropped today.
- Only non-consuming option: wrap the agent's StreamingChatModel (AiServiceContext.streamingChatModel, read by QuarkusAiServiceTokenStream through effectiveStreamingChatModel) with a tee of onPartialResponse into Dev UI events. Internal and fragile: AgentInvocationHandler overwrites the field on each call for agents with a streamingChatModelProvider, and a @ModelName override bypasses it.
- The root agent's stream of a Dev UI run is consumed by the Dev UI itself (the run invoker is the caller): it can be shown without any hook.
- Token events must be batched (event thread, websocket, the per-frame publishing of the run panel).

## Open questions
- Is covering only fixed-model agents (plus the root stream) acceptable for a first version?
- Restore of the wrapper on hot reload (new agents) and when the model provider function replaces it.
- Granularity: batch per N ms or per N tokens?

## Related
Shares the per-agent AiServiceContext hook with DRAFT-6; lower priority than DRAFT-6 (higher risk, narrower benefit).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 In a Dev UI run, the text of a streaming root agent grows live in its timeline row and node details
- [ ] #2 Inner streaming agents with a fixed model show their tokens live too; agents the hook cannot reach show their output at the end, as today, and the node says live tokens are unavailable
- [ ] #3 The application's stream is never consumed twice nor blocked, and application calls are not observed
- [ ] #4 Token events are batched so a fast stream does not flood the event thread or the websocket
- [ ] #5 Tests cover a streaming root agent, a streaming inner agent and an agent the hook cannot reach
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it needs internal langchain4j hooks (no public API yet). Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
