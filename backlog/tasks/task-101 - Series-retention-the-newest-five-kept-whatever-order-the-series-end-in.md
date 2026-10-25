---
id: TASK-101
title: 'Series retention: the newest five kept whatever order the series end in'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-25 20:28'
updated_date: '2026-10-25 20:30'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 157000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Nightly snapshot build (2026-10-25): SeriesTest.moreThanFiveSeriesRunningAtOnceAreBoundedWhenTheyEnd failed at "an older one left once ended" (size 5 held). pruneSeries drops ended series oldest first until five are left, skipping running ones: when newer series end before an older one still running, the newer ones are dropped and the older one stays for good once five are left. A series status is also set before its system series are pruned, so a reader may see it ended while it is still about to be dropped.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Once the series of a system have all ended, the five newest are kept, whatever order they ended in
- [x] #2 A series older than the five newest leaves as soon as it ended; running ones are never dropped
- [x] #3 A series to be dropped when it ends is never seen ended and still kept (the drop and the end are one step)
- [x] #4 Tests: series ending in reverse order of their start leave the five newest
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. pruneSeries: drop only the ended series older than the SERIES_KEPT newest (a fixed window, not a count reached oldest first).
2. end(): under the seriesHistory lock, prune treating the ending series as ended, then set its status, so a series to be dropped is gone before it reads ended.
3. SeriesTest: 7 running series cancelled newest first leave the five newest; run SeriesTest and the integration suite.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Cause confirmed: pruneSeries counted down from the oldest until five were left, skipping running ones, so newer series ending first were dropped and an older one still running stayed once it ended. The new test (the six newer cancelled and ended first, then the oldest) fails on the old code deterministically (kept the oldest, dropped the third newest) and passes on the fix. AC3: end() prunes (treating the ending series as ended) then sets the status, both under the seriesHistory lock; the test checks the oldest is gone right after it reads ended, but the interleaving itself cannot be forced without a hook. Verification: SeriesTest 10/10 four runs; mvn -o clean verify -pl integration-tests -Ppatterns: 172 tests, BUILD SUCCESS (a verify without clean failed on stale pattern classes in target, unrelated).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Series retention now keeps the five newest series of a system, whatever order they end in: pruneSeries drops only the ended series older than the five newest (running ones are never dropped), and end() drops a leaving series before it reads ended, in one step under the seriesHistory lock. Fixes the nightly snapshot failure of SeriesTest.moreThanFiveSeriesRunningAtOnceAreBoundedWhenTheyEnd, whose test now ends the newer series first so that it fails deterministically on the old rule. Verified with SeriesTest (repeated) and the integration suite with -Ppatterns.
<!-- SECTION:FINAL_SUMMARY:END -->
