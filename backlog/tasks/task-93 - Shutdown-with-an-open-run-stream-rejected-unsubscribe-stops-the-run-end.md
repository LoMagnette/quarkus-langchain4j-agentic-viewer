---
id: TASK-93
title: 'Shutdown with an open run stream: rejected unsubscribe stops the run end'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-21 15:05'
updated_date: '2026-10-21 15:14'
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
- [x] #1 After a shutdown, a run with an open stream ends CANCELLED, its stream completes and its completion completes
- [x] #2 No RejectedExecutionException is thrown on the event thread during the shutdown
- [x] #3 Both CI configurations build
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunShutdownTest also waits for run.completion() (fails before: timeout). 2. RunManager.stream: an unsubscribe rejected by the stopped event executor is dropped (nothing is delivered any more). 3. Full builds.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RunShutdownTest waits for run.completion(): timed out before the fix (and logged the RejectedExecutionException), passes after with no rejection logged. Full builds: mvn clean install (deployment 201, integration-tests 107+6: SUCCESS), -Ppatterns (210, 145, 6: SUCCESS). The CI failure of 938318c that led here was a Maven Central 502 while QuarkusDevModeTest re-resolved the app model (quarkus-devtools-message-writer), unrelated.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RunManager.stream: an unsubscribe rejected by the stopping event executor is dropped, so the run end started by shutdown() no longer dies on the event thread and the run's completion completes. RunShutdownTest now asserts it (failed before). Both CI configurations build.
<!-- SECTION:FINAL_SUMMARY:END -->
