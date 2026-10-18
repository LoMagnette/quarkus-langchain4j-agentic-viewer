---
id: TASK-76
title: Follow-ups of an independent review (2026-10-18)
status: Done
assignee:
  - '@claude'
created_date: '2026-10-18 12:57'
updated_date: '2026-10-18 13:10'
labels:
  - code-review
dependencies: []
priority: medium
type: bug
ordinal: 127000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
An independent reviewer reported four findings on the current main. (1) Generated config docs stale for loop-warning-iterations: checked, not reproducible (main has the option in both includes; regenerating gives no diff; likely a working tree from before the TASK-74 commit). (2) RunManager trimmed the history only when a run started, so runs running when the history was full stayed forever once ended. (3) A late getInputSchema failure could overwrite the state of the system shown since in the run panel. (4) RunManager kept the caller's input map: later mutations changed what the run received and its RunStarted event.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The run history keeps at most history-size ended runs per system once runs that were running when it was full end, without another run starting; running runs are never dropped
- [x] #2 A late input-schema error of a previous system is not shown on the system the run panel shows since
- [x] #3 A run's inputs, what the agents receive and its RunStarted event do not change when the caller mutates its map after startRun
- [x] #4 The generated config reference contains loop-warning-iterations and a build leaves it unchanged
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Finding 1 (stale config docs) not reproducible on main: both includes already list loop-warning-iterations (committed in 3e67f10) and regenerating them gave no diff; the reviewed tree probably predated that commit. The includes change in this task only because the history-size Javadoc was reworded.
Finding 2: history-size now bounds the ended runs of a system; running runs are always kept and pruned when they end (prune on the event thread before run.end, so whoever waits on completion sees the bounded history).
Finding 3: the input-schema catch in qwc-agentic-run-panel.js checks the system id, like the success path.
Finding 4: startRun keeps an unmodifiable copy of the inputs (null values kept).
Tests written to fail first: RunHistoryTest.runsStillRunningWhenTheHistoryIsFullAreDroppedOnceTheyEnd, aRunKeepsItsInputsAsTheyWereWhenItStarted; AgenticSystemsPageUiTest.aLateInputSchemaErrorIsNotShownOnTheNextSystem.
Builds: default BUILD SUCCESS (deployment 171, integration-tests 106), -Ppatterns BUILD SUCCESS (175 with 1 skipped, 127), ui-tests 27/27.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Addressed the independent review: run history bounded after runs end (not only at start), runs keep a copy of their inputs, a late input-schema failure no longer shows on another system. The stale-docs finding did not reproduce (includes already up to date); the includes were regenerated for the reworded history-size description. Covered by new failing-first tests; both CI configurations and the browser tests pass.
<!-- SECTION:FINAL_SUMMARY:END -->
