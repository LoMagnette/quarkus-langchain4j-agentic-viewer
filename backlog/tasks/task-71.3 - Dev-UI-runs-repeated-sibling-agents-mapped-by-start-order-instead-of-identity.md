---
id: TASK-71.3
title: 'Dev UI runs: repeated sibling agents mapped by start order instead of identity'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 19:29'
labels:
  - code-review
  - runs
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 109000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DevUiAgentListener.childNodeId maps a repeated sibling class (~2, ~3 node ids) by how many of that class already started. In a conditional workflow {X, X} where only the second condition holds, the step is reported on /X instead of /X~2. langchain4j already gives each sub-agent a unique agentId, and nodeId() resolves by agentId position; there are two node id algorithms in the listener.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A conditional workflow with two sub-agents of the same class where only the second runs highlights the node of the second
- [x] #2 Sub-agents are resolved by identity (agent id) everywhere in the listener; start order only tells apart one agent listed several times (a declarative system lists one agent bean, sharing its agent id)
- [x] #3 A test covers the case above
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. One suffixes(agent) helper: positions of the agent among its parent's sub-agents by agent id (mapper item = its agent), used by childNodeId and nodeId.
2. childNodeId: a unique position wins; several positions (same agent listed twice, declarative) cycle by start count under the parent invocation.
3. RepeatedSiblingsTest: builder conditional with two ItemFinder agents, only the second active; fails before.
4. Full builds.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding: in a declarative system the same sub-agent class listed twice is one agent (same agent id, TwiceExclaimed), so identity alone cannot tell the two positions apart; start order stays for that case only. Builder systems with two instances of a class are now resolved by identity (RepeatedSiblingsTest failed before: reported /ItemFinder instead of /ItemFinder~2).

RepeatedSiblingsTest fails before, passes after; SampleRunEventsTest.sameSubAgentTwiceMapsToTwoNodes still passes (declarative same agent twice). Both CI configurations: BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Sub-agent node ids are resolved by agent identity through one helper shared by the listener's two id paths; start order only remains for one agent listed several times (declarative), whose positions share an agent id. Verified with RepeatedSiblingsTest (builder conditional, only the second of two same-class agents runs) and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
