---
id: TASK-71.10
title: 'Dev UI run panel: stream errors, cancellation and stale responses'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:25'
labels:
  - code-review
  - dev-ui
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: medium
type: bug
ordinal: 116000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
qwc-agentic-run-panel: the streamRun subscription has no error handling, so a stream error (unknown or evicted run) or a websocket drop leaves the panel on Running forever with the pulse and timers going. After a cancel, agents in flight stay highlighted as running because RunManager drops their finish events. startRun and getRun responses are not checked against the latest user action: a started run can be merged into a history entry opened meanwhile, and two quick clicks on running history rows leak a subscription. A getRun failure sets _run but not _mode. A waiting human step with a null invocationId is never removed after the answer. An answer for a step typed Object or CharSequence is sent as JSON-parsed although the server passes it through as text. Tool calls without id of the same tool in one invocation are paired wrongly in the timeline.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A stream error or disconnect ends the live view with a visible error and stops highlighting
- [x] #2 After a cancel no node stays highlighted as running and timeline rows of interrupted agents show they were interrupted
- [x] #3 A response to an earlier action never replaces the run the user opened since, and at most one stream subscription is open
- [x] #4 Answering a human step removes its answer box whatever its invocation id
- [x] #5 Two calls of the same tool without ids in one invocation each end their own timeline row
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run panel: onError on the run stream and an observer of the Dev UI connection state (disconnect, hot reload) end the live view as 'Connection lost' with the reason, interrupting what was shown running.
2. LiveRun.interrupt() on the terminal event (and on a lost stream): nodes still running become 'interrupted' (new graph state); the timeline marks rows still running at the run end as interrupted.
3. An action token for startRun/getRun: late responses of earlier actions are ignored; _follow stops any open stream first; a getRun failure switches to the live view to show the error.
4. Waiting steps without invocation id are removed by node; Object/CharSequence answers are text; timeline pairs id-less tool calls of one tool FIFO.
5. Browser tests for each.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Browser tests added: aCancelledRunLeavesNothingRunning, aFailingStreamEndsTheLiveView (the client's onErrorCallback, as on a JSON-RPC error), aDisconnectEndsTheLiveView (connectionState set disconnected as the client does), aLateStartResponseDoesNotReplaceTheRunOpenedSince (startRun delayed in the page), stepsWithoutInvocationIdAndToolCallsWithoutIdEnd (LiveRun and buildTimeline with synthetic events). The cancel, stream error, late response and step tests fail on the previous JS (checked by stashing graph/timeline/run-panel). ui-tests profile: 14 tests pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The run panel no longer gets stuck or mixed up: a stream error, disconnect or hot reload ends the live view with a visible reason, a cancelled run leaves nothing pulsing (nodes and timeline rows show interrupted), late responses never replace the run opened since and at most one stream is open, answered steps without invocation id disappear, Object/CharSequence answers are sent as text, and id-less calls of one tool pair in order. Verified with five new browser tests (failing before) and the full ui-tests profile.
<!-- SECTION:FINAL_SUMMARY:END -->
