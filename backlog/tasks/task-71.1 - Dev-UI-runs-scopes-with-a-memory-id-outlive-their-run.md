---
id: TASK-71.1
title: 'Dev UI runs: scopes with a memory id outlive their run'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 19:12'
labels:
  - code-review
  - runs
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: high
type: bug
ordinal: 107000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DevUiAgentListener maps a scope to its run in afterAgenticScopeCreated and forgets it only in beforeAgenticScopeDestroyed. langchain4j (DefaultAgenticScope.rootCallEnded, 1.20.2) only evicts EPHEMERAL scopes at the end of the root call; a scope with a memory id stays registered and is reused by getOrCreateAgenticScope without calling afterAgenticScopeCreated again. A second Dev UI run with the same memory id is therefore attributed to the first (ended) run: its events are dropped and its human step fails with CancellationException; an application call of the root with that memory id has its human step intercepted by the Dev UI. CdiRunInvoker also invents a UUID memory id when the form leaves it empty, turning every such run into a REGISTERED scope that stays in the app registry and keeps the Run (with all events) and its error observer alive, bypassing history-size.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Two consecutive Dev UI runs with the same memory id each get their own events, and a human step in the second run waits for Dev UI input of the second run
- [x] #2 An application (non Dev UI) call of a root with a memory id used by an earlier Dev UI run is not intercepted and emits no Dev UI events
- [x] #3 A run whose memory id was left empty leaves no scope in the application registry and no reference to the Run in the listener once it ended
- [x] #4 Tests cover the three cases above
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. DevUiAgentListener: bind the scope to the current run at the root call of a Dev UI run (beforeAgentInvocation of a parentless agent on a run thread), since a reused scope gets no afterAgenticScopeCreated.
2. DevUiAgentListener: unbind every scope of a run when its RunFinished/RunFailed is delivered (RunManager listener); also drop its error observer and tool starts.
3. CdiRunInvoker: evict (AgenticScopeAccess.evictAgenticScope) the scope of a memory id made up for the run after the root call; only make up ids for String-compatible or UUID parameters.
4. Test-only system MemoryOrder (root with @MemoryId and a human step) and MemoryIdRunTest for the three cases; check the tests fail without the fix.
5. Full build.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DevUiAgentListener binds the scope to the run at the root call of a Dev UI run (a reused scope gets no afterAgenticScopeCreated) and unbinds every scope of a run when its RunFinished/RunFailed is delivered (also drops its error observer and tool starts). CdiRunInvoker evicts the scope of a memory id made up for the run (AgenticScopeAccess.evictAgenticScope, ScopeDestroyed is emitted before RunFinished) and only makes up ids for String-compatible or UUID @MemoryId parameters. Test-only system MemoryOrder + MemoryIdRunTest (3 tests): all three fail on the previous code (second run fails with 'Human input of approve not given', the application call is intercepted, no ScopeDestroyed) and pass now. mvn -o clean install: BUILD SUCCESS (deployment 125, integration-tests 101 tests).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A Dev UI run now owns its AgenticScope only while it runs: the root call of a run binds the scope (also a reused one, same memory id) and the end of the run releases it, so a second run with the same memory id gets its own events and human steps, and an application call reusing the memory id is not intercepted. The scope of a memory id made up for a run is evicted after the run. Verified with MemoryIdRunTest (fails before, passes after) and a full clean install.
<!-- SECTION:FINAL_SUMMARY:END -->
