---
id: TASK-34
title: Derive ScopeStateChanged events and truncate payloads
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:17'
labels:
  - runtime-dev
  - live-view
milestone: m-2
dependencies:
  - TASK-31
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not a separate spec task; covers the ScopeStateChanged event and F6.3. The scope panel (T2.9) needs per-key updates, and large scope values or outputs would slow the UI and use memory.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A ScopeStateChanged event is emitted for each key written by an agent output
- [x] #2 Every value in event payloads is truncated to a configurable size (default 2 KB) with a truncation marker
- [x] #3 Unit tests cover truncation of strings, collections and nested objects
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. DevUiAgentListener: after an agent with an output key returns, also emit ScopeStateChanged(key, value = the output, nodeId); no copy or conversion on the agent thread.
2. runtime-dev events/PayloadTruncator: value -> Jackson tree (stream outputs and values Jackson cannot convert become a marker, never toString'ed); when its JSON text exceeds the limit: strings cut with '… [truncated, N chars]', arrays/objects keep elements while within the budget then a '… [N more items/fields]' marker, recursively. Result: tree + truncated flag.
3. RunManager applies it on the event thread to every payload value (run inputs/output, agent inputs/output, tool arguments/result, scope values, planner extras) before recording: events only carry JSON-safe, bounded values. Limit: quarkus.langchain4j.agentic.dev-ui.max-payload-size (default 2048 characters of JSON), read with ConfigProvider (the config root comes with T6.1).
4. Tests: PayloadTruncatorTest (string, collection, nested objects, under the limit untouched, unconvertible and stream values); ScopeStateChanged for each written key in sample runs; existing assertions adapted to JSON payloads.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
From TASK-15 (decision-5): a streaming agent output can be a StreamingResponse/TokenStream whose toString() blocks until the stream completes. Never stringify or truncate such outputs inside the listener callback; emit the finished event when the delayed response completes.

DevUiAgentListener: after an agent with an output key (AgentInstance.outputKey()) returns, also emits ScopeStateChanged(key, output, nodeId) - raw value, no work on the agent thread; AgentFinished/AgentFailed now reuse the invocation's node id. events/PayloadTruncator: value -> Jackson tree (findAndRegisterModules); streaming types (TokenStream, Multi, Flow.Publisher, Stream) -> '<stream type>' and unconvertible values -> '<type, not serializable to JSON>', never toString'ed; beyond the limit strings are cut with '… [truncated, N chars]', arrays and objects keep entries within the budget then '… [N more items]' / '…: [N more fields]', recursively; Result(tree, truncated).value() gives plain Java values. Finding: the Dev UI serializer writes a Jackson JsonNode as a bean ({nodeType, empty, ...}), so events carry plain values (maps, lists, strings, numbers) - documented in CLAUDE.md. RunManager.bounded() applies it on the event thread to run inputs/output, agent inputs/output, tool arguments/result (text), scope values (truncated flag) and planner extras; limit from quarkus.langchain4j.agentic.dev-ui.max-payload-size (default 2048 chars of JSON) via ConfigProvider, constructor parameter for tests (the config root is T6.1). Tests: PayloadTruncatorTest (7: small values kept as plain JSON, long string, long collection with item count, nested objects shrunk inside, map values one by one, text payloads, stream and unserializable markers), SampleRunEventsTest.writtenKeysAreReportedAsScopeChanges (StoryWriter: intro, adventure, story with their nodes and values), dev-mode streamRun shows plain inputs. Validation: deployment 93, integration-tests 50, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Each key written by an agent output now produces a ScopeStateChanged event, and every payload value of the events is converted to plain JSON values and truncated beyond a configurable size (default 2 KB) with markers, on the event thread. Verified by truncation unit tests (strings, collections, nested objects, streams) and sample runs over the Dev UI websocket.
<!-- SECTION:FINAL_SUMMARY:END -->
