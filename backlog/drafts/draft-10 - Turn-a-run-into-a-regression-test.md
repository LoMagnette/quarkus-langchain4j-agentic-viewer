---
id: DRAFT-10
title: Turn a run into a regression test
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 06:13'
labels:
  - idea
  - dev-ui
  - testing
  - postponed
dependencies: []
priority: low
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
A behaviour seen in the Dev UI (good or bad) is lost once the session ends; reproducing it in a test means writing a fake model script by hand. Turning a run into a regression test makes "it worked in the Dev UI" repeatable, without a real model or API key.

## Scope
From a past run in the history:
- export the run trace (JSON: inputs, events, and per model call the request and the reply);
- generate a JUnit test (or a test resource plus a generic runner) that replays the recorded model replies through a replay chat model and asserts each agent output and the run output.
Out of scope: tools and non-AI agents with side effects run for real (see DRAFT-15 mock tools); streaming replay in a first version.

## Feasibility (research 2026-10-18)
- Needs the per-round model requests and replies of DRAFT-6 (request messages, tool specifications, reply AI message with tool requests).
- Replay matching must not depend on call order (parallel branches): key on a normalized request (system message, last user message, last tool result) with a queue per key for loops; varying content (dates, memory ids) needs normalization rules.
- The replay model, trace loader and assertions cannot go in the runtime artifact (inert types only, checked by ProdArtifactIT): a new test-scope artifact, e.g. quarkus-langchain4j-agentic-viewer-junit. runtime-dev writes the trace and the generated test source.
- Wiring in the application's tests: an @Alternative model bean in a test profile; named models (@ModelName) need named alternatives.
- The integration-tests ScriptedChatModel is a starting point (first-match rules, text and tool-call replies, request log) but is sync-only.

## Open questions
- Generate Java source, or ship the trace plus a generic parameterized runner?
- Matching and normalization rules; what to do on an unmatched request (fail with a diff of the closest recorded request?).
- Artifact coordinates and whether the generated test targets @QuarkusTest or a plain unit test.

## Related
Depends on DRAFT-6. Completed by DRAFT-15 (mock or replay tool calls).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A past Dev UI run can be exported as a JSON trace holding its inputs, events, and the request and reply of every model call
- [ ] #2 A test generated from a run (or the trace with the provided runner) passes against the unchanged application without any real model, and fails with a readable difference when an agent output changes
- [ ] #3 Replay does not depend on the order of parallel model calls and handles loops (same request asked several times)
- [ ] #4 The replay support lives in a test-scope artifact; nothing of it reaches the production artifact (ProdArtifactIT)
- [ ] #5 Documented in index.adoc with an example; tests cover a sequence, a parallel workflow, a loop and a tool round
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Postponed 2026-10-18 (owner's decision): no new access to internal langchain4j API for now; it depends on DRAFT-6's recorded model calls, which need an internal hook. Revisit when langchain4j/quarkus-langchain4j offer a public API for it (see the upstream asks in the description) or if the decision changes.
<!-- SECTION:NOTES:END -->
