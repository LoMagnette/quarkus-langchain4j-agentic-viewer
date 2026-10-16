---
id: TASK-41
title: JSON-RPC event sequence tests per pattern kind
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 15:02'
labels:
  - testing
  - json-rpc
milestone: m-2
dependencies:
  - TASK-30
  - TASK-32
  - TASK-33
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the spec testing strategy: "start a run, subscribe, assert the event sequence for sequence, loop, parallel, conditional, supervisor and each planner". Guards the listener, correlation and broadcaster against regressions and upstream API changes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Tests start a run over JSON-RPC, subscribe and assert the exact event sequence for sequence, loop, parallel, parallel mapper, conditional and supervisor samples
- [x] #2 Tests run on the fake ChatModel in CI
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. AgenticViewerJsonRpcService gets constructor injection (registry, run manager) so tests can build it; integration-tests JsonRpcEventSequenceTest (@QuarkusTest, scripted model) builds the registry from the scanned samples, a RunManager with CdiRunInvoker and the Dev UI listener on the roots, then for each pattern calls startRun and subscribes to streamRun (Multi, AssertSubscriber) until completion.
2. Event sequences as compact lines (type + short node + iteration/item + key): exact global sequence for sequence (StoryWriter), loop (StoryCreator), conditional (ExpertRouter), supervisor (BankSupervisor, with tools); for parallel (ResearchGatherer) and parallel mapper (PhraseTranslator), whose branches interleave: exact root-level sequence and exact per-branch sequence.
3. Runs on the scripted fake model (no API key), part of the normal build.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Parallel mapper: items interleave freely (finishes and per-item keys), and each item writes its own scope key translation_<index> (upstream behaviour, not 'translation'); the test asserts the fixed prefix/suffix exactly, the middle as a multiset and that each item's key follows its start. Websocket transport is already covered by AgenticSystemsDevModeTest; this test calls the service directly (constructor injection) because runtime-dev beans only exist in dev mode. Stable over 3 consecutive runs; full build: deployment 97, integration-tests 59, all passing.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
JsonRpcEventSequenceTest (integration-tests, scripted fake model) starts a run through AgenticViewerJsonRpcService.startRun, subscribes to streamRun and asserts the exact event sequence for sequence (StoryWriter), loop (StoryCreator, 3 iterations), conditional (ExpertRouter), supervisor with tools (BankSupervisor), and for parallel (ResearchGatherer) and parallel mapper (PhraseTranslator) the exact root-level and per-branch sequences. The service now uses constructor injection so tests can build it. Verified with the full build (deployment 97, integration-tests 59 tests passing).
<!-- SECTION:FINAL_SUMMARY:END -->
