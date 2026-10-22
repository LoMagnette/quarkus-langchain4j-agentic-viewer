---
id: TASK-98
title: 'Test tab: run a system N times with the same inputs and compare the results'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 18:01'
updated_date: '2026-10-22 18:47'
labels: []
dependencies: []
priority: medium
ordinal: 154000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Owner idea (2026-10-22): a Test tab to run the same agentic system n times and see the different results. Agentic systems are not deterministic (model sampling, supervisor and planner choices, loop counts, tool results): one run says little, a series shows how stable the output, the path and the cost are. Today the owner would have to click Run again n times and compare runs two by two. Each run calls the real models and tools, so n runs cost n times as much.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 From a separate Dev UI page (owner decision), the same inputs of a system can be run N times (1 to 50), one run after the other (owner decision: in sequence for now), with progress and a way to stop the series
- [x] #2 The results show each run (status, duration, tokens, output) and how they differ: identical outputs grouped with their count, the paths taken (agents invoked per run) grouped the same way, success rate and duration and token spread
- [x] #3 Any run of the series opens in the live view, and two of them open in the comparison
- [x] #4 A series does not push the other runs out of the history (kept as one series), and the tools confirmation and cost warning tell that N runs are started
- [x] #5 Tests cover a series (results, grouping, stop) and a browser test runs one; docs updated
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Owner decisions (2026-10-22): a separate Dev UI page (not a tab of Agentic Systems), runs in sequence only, N at most 50.
1. runtime-dev: Series (id, system, inputs, count, status, run ids, current run, cancel flag); RunManager.startSeries(systemId, inputs, count) runs the N Dev UI runs one after the other on a series thread (each waits for a run thread permit instead of being refused), cancelSeries (stops and cancels the current run), series(id), listSeries(systemId); series runs carry their series id and stay out of the system history; 5 series kept per system (an older ended one leaves with its runs); shutdown cancels them.
2. Results computed on the server from the recorded events (SeriesRun: index, run id, status, duration, tokens, bounded output, error, path = node ids started in order); JSON-RPC startSeries, getSeries, listSeries, cancelSeries.
3. Page qwc-agentic-test-runs.js, second page of the card ("Agentic Test Runs"): system picker, run form (submit label), N (1..50), confirmation telling N runs and the reachable tools, progress + Stop (polls getSeries, follows the current run live on the graph, answers its human steps), results: summary (success rate, duration and tokens min/median/max), distinct outputs and distinct paths grouped with counts, run table (click: replay on the graph and timeline; tick two: comparison dialog), past series of the system.
4. Tests: integration SeriesTest (results, sequence, out of history, bounds, cancel, human step), dev-mode JSON-RPC, Playwright test of the page; docs, CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC 1 reworded to the owner decisions (separate page, in sequence, max 50). Series runs are Dev UI runs: their human steps wait for an answer on the page (each run). The page polls getSeries every 500 ms while running and follows the current run with streamRun (graph live, answer box); results (outputs and paths grouped, duration/token spread) come from SeriesRun computed on the server from the bounded events; paths shown without the root. Verification: integration SeriesTest 6/6, suites 128 (default) / 168 (patterns) incl. ITs (scratch copy, committed pom); deployment 204 incl. AgenticSystemsDevModeTest#aSeriesRunsTheSystemNTimes; Playwright 37/37 incl. AgenticTestRunsPageUiTest (series with live graph, grouping, replay, comparison; stop; human steps answered per run). Screenshot checked.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
New Dev UI page "Agentic Test Runs": pick a system, fill its inputs once, run it N times (1-50) one after the other. RunManager.startSeries runs the Dev UI runs on a series thread (each waits for a run thread, Stop cancels the current one), keeps the runs with the series (5 series per system) and out of the run history, and gives per-run results (status, duration, tokens, bounded output, error, path). The page follows the run going on live on the graph (and answers its human steps), then shows success rate, duration/token spread, distinct outputs and distinct paths with their counts, and every run; a run replays on the graph and timeline, two compare in the comparison dialog. JSON-RPC startSeries/getSeries/listSeries/cancelSeries. Verified by SeriesTest, a dev-mode JSON-RPC test, AgenticTestRunsPageUiTest and the full suites.
<!-- SECTION:FINAL_SUMMARY:END -->
