---
id: TASK-99
title: >-
  Series: retention after the end, bounded series threads, stale errors on the
  test runs page
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 19:10'
updated_date: '2026-10-22 19:17'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 155000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Review of TASK-98 (2026-10-22): (1) series are pruned only when one starts, and running ones are never pruned, so more than five series running at once escape the limit for good once they end (series, runs and events kept forever); (2) every accepted series takes a thread of an unbounded cached pool, even when every run thread is busy, so repeated startSeries calls create threads without limit, blocked waiting for a run thread; (3) on the Agentic Test Runs page, failure handlers of the topology, schema, series list and start calls do not check that the system is still the selected one, and selecting a system does not clear an earlier error: a late failure of system A shows on system B or clears its series list.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 When a series ends, the series of its system are bounded again to the newest five (and every one still running): a test with more than five series running at once ends with five kept
- [x] #2 At most max-concurrent-runs series run at once: a further startSeries is refused with a clear error (no thread created), and accepted again once one ends
- [x] #3 Every response handler of the test runs page ignores responses for a system no longer selected, and selecting a system clears the error shown; a browser test checks a late failure does not reach the next system
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunManager: end(series) gives back a series slot and prunes the series of its system under the seriesHistory lock; seriesSlots semaphore (max-concurrent-runs) taken in startSeries before anything is created, refused with a clear error.
2. Test runs page: every response handler checks the system (or run) is still the current one; _select clears _error.
3. Tests: SeriesTest (7 running series bounded to 5 once ended; a 3rd series refused with max-concurrent-runs=2, accepted once one ended); Playwright late failure of another system.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
All three review findings confirmed in the code before fixing; _confirm, _show and _sendAnswer failure handlers had the same missing guard and are fixed too. Verification: SeriesTest 8/8; integration suite green (scratch copy, committed pom); deployment 204; AgenticTestRunsPageUiTest 4/4 (the new test fails on the old page code: the late error showed and the series list was cleared).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed the three review findings of TASK-98: a series ending bounds the series of its system again (running ones were never pruned, so more than five escaped the limit for good); at most max-concurrent-runs series run at once, a further startSeries is refused before any thread or series is created; the test runs page ignores responses for a system no longer selected (and runs no longer shown), and selecting a system clears the error. Verified by two new SeriesTest cases, a new Playwright test and the full suites.
<!-- SECTION:FINAL_SUMMARY:END -->
