---
id: TASK-93
title: 'Shutdown with an open run stream: rejected unsubscribe stops the run end'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-21 15:05'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 149000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found while looking at a red CI run (938318c, with-patterns): its annotations show "RunManager$$Lambda rejected from ThreadPoolExecutor[Shutting down]", which every local build prints too during RunShutdownTest. RunManager.shutdown ends the open runs on the event thread; Run.end completes their streams, whose onTermination submits the unsubscribe to the event executor, already shutting down: RejectedExecutionException on the event thread, so Run.end stops before completion.complete and the run completion never completes (whoever waits on it after a reload hangs). Not the cause of that CI failure (AgenticSystemsDevModeTest.listSystems: Unable to create the archive, cause not in the pasted log).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 After a shutdown, a run with an open stream ends CANCELLED, its stream completes and its completion completes
- [ ] #2 No RejectedExecutionException is thrown on the event thread during the shutdown
- [ ] #3 Both CI configurations build
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunShutdownTest also waits for run.completion() (fails before: timeout). 2. RunManager.stream: an unsubscribe rejected by the stopped event executor is dropped (nothing is delivered any more). 3. Full builds.
<!-- SECTION:PLAN:END -->
