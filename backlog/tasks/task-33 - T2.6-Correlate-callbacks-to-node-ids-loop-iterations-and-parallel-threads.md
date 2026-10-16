---
id: TASK-33
title: 'T2.6: Correlate callbacks to node ids, loop iterations and parallel threads'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:13'
labels:
  - runtime-dev
  - live-view
milestone: m-2
dependencies:
  - TASK-31
  - TASK-20
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.6. Parallel and async agents interleave their callbacks, so mapping by agent name alone would highlight the wrong node. Correlate by scope, agent id and invocation id.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each agent event carries the topology nodeId, an invocationId and its parent invocationId
- [x] #2 Loop events carry the iteration number and parallel-mapper events the item index
- [x] #3 Parallel and mapper sample runs map every event to the right node
- [x] #4 The same sub-agent used twice in one system maps to two distinct nodes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. AgentStarted gains itemIndex (parallel-mapper item, null otherwise); iteration stays the loop index.
2. DevUiAgentListener keeps, per open invocation, its node id, iteration, item index and child bookkeeping; node kinds come from the run's topology (cached per system). A direct child of a LOOP invocation gets the loop counter, incremented when the loop's first sub-agent starts; a child of a PARALLEL_MAPPER invocation gets the index of its input value in the mapper's item list (from the mapper's inputs; indexes already taken are skipped so duplicates stay distinct; next free index otherwise), synchronized as items run concurrently; other descendants inherit iteration and item index from their parent invocation.
3. Tests (integration-tests SampleRunEventsTest): StoryCreator score/edit iterations 0,1,2 (loop and drafter none); PhraseTranslator translate starts get item indexes 0..2 matching their input phrase; ResearchGatherer branches (worker threads, repeated runs) map to their nodes with the root invocation as parent; a test-only sequence using the same sub-agent class twice maps its two invocations to the two distinct nodes (/X and /X~2).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Already in place from T2.4: nodeId from the AgentInstance chain (TopologyBuilder rules), invocationId per callback pair (per-thread stack keyed by agentId), parentInvocationId (thread stack, else the parent's open invocation for branches on other threads). Remaining here: loop iteration index, parallel-mapper item index, same sub-agent twice -> two nodes, dedicated tests for parallel and mapper runs.

AgentStarted gained itemIndex. DevUiAgentListener: each open invocation keeps its node id, iteration, item index, loop counter, mapper items and per-class child start counters; node kinds from the run's topology (cached per system). Child node id = parent invocation's node id + '/' + sub-agent simple class name, resolved against the parent node's topology children; when the parent lists the class several times (/X, /X~2) the k-th start under the parent invocation takes the k-th (cycling for loops). Findings: the two copies of a repeated sub-agent share an agentId, so the AgentInstance position lookup gave both /X; parallel-mapper item invocations are named <agent>_<item index> and get empty inputs, so the item index is read from that suffix (input matching and next free index remain as fallbacks). Loop: a direct child of a LOOP invocation takes the loop counter, incremented when the loop's first sub-agent starts; deeper descendants inherit iteration and item index. Tests (integration-tests SampleRunEventsTest, 7): StoryCreator create/draft/polish null then score/edit 0,1,2 with the loop invocation as parent; PhraseTranslator x5: three Translator starts with item indexes {0,1,2}, each matching its output (bonjour/merci/bonne nuit); ResearchGatherer x5: each branch on its node with the root invocation as parent; test-only TwiceExclaimed (src/test, sequence of the same Exclaimer twice) -> root, /Exclaimer, /Exclaimer~2 and output hello!!. Validation: deployment 86, integration-tests 49, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Agent events now carry the right topology node, invocation and parent invocation even for interleaved parallel branches, the loop iteration for loop bodies, the item index for parallel-mapper items, and distinct nodes for a sub-agent listed twice. Verified by repeated parallel and mapper sample runs and a test-only system using the same sub-agent twice.
<!-- SECTION:FINAL_SUMMARY:END -->
