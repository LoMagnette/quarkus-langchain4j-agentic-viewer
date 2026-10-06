---
id: TASK-15
title: Decide how streaming agents returning TokenStream are shown
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:14'
labels:
  - decision
  - dev-ui
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
priority: low
type: spike
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Open question from spec-breakdown.md. Agents returning `TokenStream` produce output incrementally. The live view can show tokens as they arrive or only the final output; the choice affects event volume, truncation (F6.3) and the listener design.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A Backlog decision records token-by-token vs final-output display for TokenStream agents, with rationale
- [x] #2 The decision states the impact on event volume and truncation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision recorded as decision-5 (accepted). Based on langchain4j-agentic 1.20.2-beta30 sources (no streaming sample exists yet).

Facts:
- AgentListener has no partial-token callback; only before/after/error per invocation.
- Inside a workflow, a sub-agent returning TokenStream is wrapped in a StreamingResponse (AgentExecutor: planner.propagateStreaming() ? tokenStream : new StreamingResponse(tokenStream)), which buffers the stream into the final text; only the root (UntypedAgent or TokenStream return type, allowStreamingOutput) can hand the stream to the caller.
- A streaming agent's output seen by a listener can be a StreamingResponse / TokenStream; StreamingResponse.toString() blocks until completion (result()). A listener must not stringify such outputs inline (F6.2: callbacks never block).

Decision: the live view shows the final output when the stream completes, with a "streaming" marker on the node while it runs. No token-by-token display.

Rationale and impact:
- Event volume: token-by-token would emit one event per token (hundreds per answer) for every streaming agent, multiplying stream traffic and history size; final-output-only keeps one AgentFinished per invocation.
- Truncation (F6.3): only the final text is truncated (default 2 KB), once.
- Cost: token-by-token would require wrapping TokenStream / StreamingChatModel instances; revisit only if upstream adds partial-response callbacks to AgentListener.
- Follow-up for T2.4/T2.6b: detect DelayedResponse / TokenStream outputs, emit AgentFinished when they complete (completion callback or isDone), never block in the listener.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Decided (decision-5): streaming agents show their final output when the stream completes, with a streaming marker while running; no token-by-token display. AgentListener has no partial-token callback and workflows buffer sub-agent streams anyway; token events would multiply event volume. Noted that StreamingResponse outputs block on toString, so listeners must not stringify them inline.
<!-- SECTION:FINAL_SUMMARY:END -->
