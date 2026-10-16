---
id: TASK-33
title: 'T2.6: Correlate callbacks to node ids, loop iterations and parallel threads'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:05'
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
- [ ] #1 Each agent event carries the topology nodeId, an invocationId and its parent invocationId
- [ ] #2 Loop events carry the iteration number and parallel-mapper events the item index
- [ ] #3 Parallel and mapper sample runs map every event to the right node
- [ ] #4 The same sub-agent used twice in one system maps to two distinct nodes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Already in place from T2.4: nodeId from the AgentInstance chain (TopologyBuilder rules), invocationId per callback pair (per-thread stack keyed by agentId), parentInvocationId (thread stack, else the parent's open invocation for branches on other threads). Remaining here: loop iteration index, parallel-mapper item index, same sub-agent twice -> two nodes, dedicated tests for parallel and mapper runs.
<!-- SECTION:NOTES:END -->
