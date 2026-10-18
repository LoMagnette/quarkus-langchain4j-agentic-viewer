---
id: TASK-71.2
title: 'Dev UI runs: invocation state shared between runs and lost for mapper items'
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
priority: medium
type: bug
ordinal: 108000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DevUiAgentListener.openInvocations is keyed by agentId only and shared by all runs; openInvocation() falls back to the newest entry of any thread. Branches of a parallel workflow (no local parent) of run A can take run B parent invocation when two Dev UI runs of the same system overlap: wrong parentInvocationId, iteration and item index. Mapper items are invoked under the agentId <id>_i, so when the mapped sub-agent is itself a workflow its children look up their parent with the plain id and get no parent, no item index and no iteration. After an agent dies with an Error (not reported by langchain4j onError) or a suspension, its invocation stays on the thread-local deque and in openInvocations; the next run on the same pooled thread nests its root under the stale invocation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Two overlapping Dev UI runs of a system with a parallel workflow report each branch under the parent invocation of its own run
- [x] #2 Children of a mapper item whose sub-agent is a workflow carry the item parent invocation and item index
- [x] #3 A run following a run whose invocations were left open on the same thread (cancelled run: its callbacks come after its end) reports its root with the root node id
- [x] #4 Tests cover the three cases above
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Invocation carries its Run; openInvocations keyed by run id + agent id; lookups and close match the run (fixes overlapping runs).
2. Local lookup also matches a parallel mapper item invocation (<agentId>_<n>) for its agent, so children of an item that is a workflow find the item.
3. beforeAgentInvocation drops invocations of other runs from the thread deque (stale: their callbacks never came); the end of a run drops its open invocations.
4. Test-only fixtures TwinSearch (parallel, gate holding branches so runs overlap), PhraseSteps (mapper over a sequence); InvocationCorrelationTest incl. a cancelled MemoryOrder run followed by a run; check each test fails without its part of the fix.
5. Full build, default and -Ppatterns.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding: an Error thrown by an agent does not leave an invocation open: langchain4j invokes agent methods reflectively and wraps it (AgentInvocationException), so onAgentInvocationError fires. Invocations are left open on a run thread by a cancelled run instead: since TASK-71.1 its scope is released at RunFailed, so its later callbacks find no run. AC 3 reworded accordingly.

InvocationCorrelationTest (fixtures TwinSearch with a gate holding 4 branches so two runs overlap, PhraseSteps mapper over a sequence, MemoryOrder cancelled then rerun): before the fix the overlap and mapper tests fail; with the thread purge disabled the cancel test fails. mvn -o clean install (deployment 125, integration 105) and -Ppatterns (129, 124): BUILD SUCCESS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Invocations are correlated per run: each carries its run, the cross-thread index is keyed by run and agent id, and a thread drops what it still holds of other runs; children of a parallel mapper item that is a workflow find the item invocation (item agent id <id>_<n>). Overlapping runs, mapper workflows and a run after a cancelled one on the same thread are covered by InvocationCorrelationTest (fails before); both CI configurations pass.
<!-- SECTION:FINAL_SUMMARY:END -->
