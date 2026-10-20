---
id: TASK-89
title: 'Runs: bound the number of concurrent runs'
status: Done
assignee: []
created_date: '2026-10-20 16:04'
updated_date: '2026-10-20 16:21'
labels: []
dependencies: []
priority: low
type: bug
ordinal: 145000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (low): `RunManager` runs on `Executors.newCachedThreadPool()`, so every concurrent `startRun` can create another platform thread, and model or tool calls may block for long or ignore interruption (a cancelled run can keep its thread). Runs may call external tools, so even in dev mode an accidental burst should be refused clearly rather than exhaust the JVM.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 At most a configured number of run threads are busy at once (cancelled runs whose thread still works count until it returns)
- [x] #2 A run asked beyond that is refused with a clear error naming the limit, and no run is created nor kept in the history
- [x] #3 The limit is a run-time config property, documented in the generated config reference
- [x] #4 Tests cover the refusal and that a slot is freed when a run thread returns
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Config max-concurrent-runs (run time, default 16) in AgenticViewerRuntimeConfig, passed to RunManager. 2. A semaphore taken in startRun before the run exists (refusal: IllegalStateException "Too many concurrent runs ..."), released when the run task returns on its thread (also after a cancel). 3. Tests with a small limit and a blocking invoker. 4. Regenerate the config reference (mvn install -pl docs).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Config quarkus.langchain4j.agentic.dev-ui.max-concurrent-runs (run time, default 16) passed to RunManager (new 5-arg constructor; the others default to 16). A Semaphore permit is taken in startRun after the system lookup and before the run exists (refusal: IllegalStateException naming the limit and the property, shown by the run panel as the start error), and given back by a wrapper around the FutureTask when the thread returns, also after a cancel before it ran or a failed submit. Config reference regenerated (mvn install -pl docs), user guide (Running a system) and CLAUDE.md updated. Tests: runsBeyondTheLimitAreRefusedUntilOneEnds, aCancelledRunKeepsItsThreadUntilItReturns. Validation: mvn -o clean install 197/107/6 (1 skipped); mvn -o clean install -Ppatterns 206 (1 skipped)/145/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
At most max-concurrent-runs (default 16) run threads are busy at once; a run beyond that is refused with an error naming the limit and nothing is created, and a cancelled run keeps its slot until its thread returns. Documented in the guide and config reference. Verified with new RunManagerTest cases and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
