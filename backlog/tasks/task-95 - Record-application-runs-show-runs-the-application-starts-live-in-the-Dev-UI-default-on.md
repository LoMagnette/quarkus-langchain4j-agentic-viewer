---
id: TASK-95
title: >-
  Record application runs: show runs the application starts, live, in the Dev UI
  (default on)
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 14:51'
updated_date: '2026-10-22 15:57'
labels: []
dependencies: []
priority: medium
ordinal: 151000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today the Agentic Systems page only shows runs started from its own run form: DevUiAgentListener ignores every invocation the Dev UI did not start, so running a system through the application (a REST endpoint, a scheduled job, e.g. the /scenarios endpoints of TASK-94) leaves the page empty. In dev mode, exercising the app through its own entry points is the common case. The listener is already attached to every root (RootListeners.attach), so application calls reach it and are dropped; TASK-61 measured +0.02 us per agent invocation when ignored vs +2.9 us when recorded, negligible next to a model call.

Owner decision (2026-10-22): record application runs by default, switchable. Risks the scope must cover: volume (load tests, polling clients or jobs in dev mode must not flood the history nor evict Dev UI runs, nor slow the application) and behaviour (an application run is observation only: no @HumanInTheLoop interception, no cancel, nothing retried or replaced). This also covers what the upstream quarkus-langchain4j-agentic Executions page does (post-hoc, MonitoredAgent roots only), relevant if the extension moves upstream (doc-1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A root invocation of a known system that is not part of a Dev UI run is recorded as an application run of that system, with its inputs, events, output or error, listed in the run history with an origin that tells it from Dev UI runs
- [x] #2 Application runs are recorded by default; a run-time config property under quarkus.langchain4j.agentic.dev-ui turns them off entirely (no application run is created, today's behaviour), documented in the generated config reference
- [x] #3 The page has a switch to pause and resume recording application runs without a restart, and a history filter (Dev UI / Application / All)
- [x] #4 An application run is observation only: its @HumanInTheLoop steps call the application method (not intercepted), it cannot be cancelled from the page, and the application gets the same result and errors as without the viewer
- [x] #5 Application runs have their own bounded history per system, so they never evict Dev UI runs; above a cap of open application runs per system further ones are not recorded, the page shows how many were skipped, and the application is never blocked or slowed by the event delivery
- [x] #6 The page can follow the latest application run of a system live (graph highlighting, timeline, scope state) as it does for a Dev UI run, and show a finished one again
- [x] #7 A root called from within an agent of another run is shown as part of that run, as today, not as a separate application run; concurrent application calls of one system are separate runs
- [x] #8 Tests: an application call shows up as a run with its events; a human step of an application run is not intercepted; the property off records nothing; the cap skips and counts; Dev UI runs are kept under application traffic; a browser test follows an application run live
- [x] #9 User documentation (docs/modules/ROOT/pages/index.adoc) and CLAUDE.md describe application runs and how to turn them off
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Config (AgenticViewerRuntimeConfig): application-runs (default true), application-history-size (20), max-open-application-runs (16, per system).
2. RunOrigin {DEV_UI, APPLICATION} on Run and RunSummary.
3. RunManager: startApplicationRun(systemId, inputs) -> Optional<Run> (no thread, no permit; empty when off, paused, shut down, unknown system, cap of open runs reached or event queue backlog -> skipped counter); endApplicationRun(run, output, error); history per origin (own size, never evicts Dev UI runs), listRuns merges newest first; cancel() refuses application runs; recording switch + status (enabled, recording, skipped per system); streamApplicationRuns(systemId) = Multi of the summaries of new application runs. Test constructors: application runs off unless enabled (existing tests unchanged).
4. DevUiAgentListener: root call (parent null) of no Dev UI run of any manager: nested under an open invocation on this thread -> joins that run; else a known system (system id given by the per-root DevUiListeners, set by RootListeners.attach) -> starts an application run (ScopeCreated emitted when the scope was just created on this thread); the root invocation closing (after / error) ends it. Stale scope mapping of an ended run is dropped on a new root call.
5. Observation only: HumanInTheLoopSteps intercepts Dev UI runs only; planner decisions and error handler observation also for application runs (already transparent); no cancel.
6. JSON-RPC: getApplicationRuns(systemId), setRecordApplicationRuns(record), streamApplicationRuns(systemId); listRuns carries origin.
7. Run panel: history badge + filter (All / Dev UI / Application), Record application runs switch (shows skipped count, or that the config turned it off), Follow application runs (opens each new one live unless a Dev UI run is running), history refreshed on new application runs; no Cancel for application runs.
8. Tests: integration-tests ApplicationRunsTest (recorded with events, HITL not intercepted, failure propagated, off, paused, cap skips and counts, Dev UI runs kept, nested root, concurrent calls, stream); dev-mode JSON-RPC test; Playwright test following an application run live.
9. Docs: index.adoc, CLAUDE.md, config reference (mvn install -pl docs).
Known limit: builder systems are observed once the Dev UI listed them (as today for their Dev UI runs); a system rebuilt per call is not.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented as planned. Decisions: application runs start at the root beforeAgentInvocation (langchain4j order: scope created -> root before -> ... -> root after -> scope destroyed), so ScopeCreated is told from a weakly kept thread-local, and ScopeDestroyed of an ephemeral scope comes after the run ended (dropped; the UI does not use it). Agent threads look systems up with the new AgenticSystemRegistry.known (find() would create builder system beans on the application thread). Test-built managers record no application runs unless enableApplicationRuns (the ~150 existing tests stay unchanged). Planner decisions are recorded for application runs (DevUiListeners.owner prefers the Dev UI run). Known limits: a root call that dies with an Error (langchain4j reports no error for an Error) leaves its application run open and counted against the cap; builder systems are observed once the Dev UI listed them, a system rebuilt per call (e.g. /builder/expert) is not observed.
Verification: integration-tests ApplicationRunsTest 10/10 (scratch copy with the committed pom: the working tree pom carries the uncommitted quarkus-flow-langchain4j dependency that breaks the module build), full integration suite 122 green; deployment 203 tests green incl. ApplicationRunsOffDevModeTest and AgenticSystemsDevModeTest#applicationRunsAreRecordedByDefault; Playwright anApplicationRunIsFollowedLive green.

Final verification: integration-tests 122 (default) and 162 (-Ppatterns) green with ITs incl. ProdArtifactIT (scratch copy, committed pom); deployment 203 green; Playwright 34/34 (AgenticSystemsPageUiTest 32 incl. anApplicationRunIsFollowedLive, which also reopens a finished application run; LargeSystemUiTest 2).

Fix after owner feedback (2026-10-22): the checkboxes did not show and nothing popped up, because the page only created the run panel after a click on Run (the Playwright test clicked Run first, hiding it). The page now keeps the run panel once a system is open (hidden), it follows application runs from the start and fires follow-run so the page shows its tab; Follow them live is on by default (unticking it is remembered per system in the browser session); the page hands the graph to the panel after each update (rendered in the same pass). anApplicationRunIsFollowedLive now opens the system only, and checks follow off lists without opening. Playwright 34/34 green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Runs the application starts itself are now recorded and shown in the Dev UI (default on). A root call of a known system outside a Dev UI run becomes an application run (RunOrigin.APPLICATION) started by DevUiAgentListener through RunManager.startApplicationRun and ended when the root call returns or fails; a system called from an agent of a run in progress joins that run. Observed only: HITL steps call the application method, cancel refuses, results/errors unchanged. Own bounded history per system, cap of open runs per system with a skipped counter, config application-runs / application-history-size / max-open-application-runs, Dev UI switch (setRecordApplicationRuns), live feed (streamApplicationRuns). Run panel: app tag, origin filter, record switch, follow live, no Cancel for application runs. Verified with ApplicationRunsTest (10), dev-mode JSON-RPC tests, Playwright anApplicationRunIsFollowedLive, full suites green. Limits documented: builder systems observed once listed by the Dev UI; a root dying with an Error leaves its application run open.
<!-- SECTION:FINAL_SUMMARY:END -->
