---
id: TASK-75
title: Re-run the last inputs after a hot reload (watch mode)
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 12:42'
labels:
  - idea
  - dev-ui
  - runs
dependencies:
  - TASK-73
priority: low
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
The usual dev loop is: change a prompt, run again with the same inputs, compare. Automating it after each hot reload (opt-in) gives immediate feedback on a change.

## Scope
Per system, an opt-in "re-run after reload" toggle: after the application reloads, the last inputs are run again and the result is compared with the previous run (DRAFT-8). Off by default; never for systems whose run would need a confirmation the user has not given (reachable tools).

## Feasibility (research 2026-10-18)
- The page learns about a reload: after each restart the Dev UI router sends a HotReload message (JsonRpcRouter.onStart), jsonrpc.js sets connectionState hotreload then connected; the run panel already observes connectionState.
- Runs do not survive a reload (RunManager shut down): keep the previous run trace in the browser (sessionStorage, like the last inputs; payloads are truncated so small).
- Starting a run at once after the reload is racy: HotReload is sent from a StartupEvent observer and RunObservation attaches the Dev UI listener in another one (no order): wait for listSystems/getTopology to answer and the system to be known, or add a server-side readiness flag. Builder systems load lazily; transform-built systems appear only once the application rebuilt them.
- A code change does not reload by itself: dev mode scans on the next HTTP request (the Dev UI websocket does not trigger it). The feature reacts to reloads triggered elsewhere (a browser refresh, an HTTP call, 's' in the console); polling to trigger reloads is a separate choice.
- Cost and safety: every reload runs real models and tools: explicit opt-in per system, debounce, never while a human step waits, show what will run.

## Open questions
- Poll to trigger the reload, or only react to reloads?
- Exclude systems with tools (side effects) or rely on the existing tools confirmation?

## Related
Depends on DRAFT-8 (comparison). Lower priority: convenience on top of DRAFT-8.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A per-system toggle (off by default) re-runs the last inputs once the application reloaded and the system is known again
- [x] #2 The new run is compared with the run before the reload (kept in the browser), using the comparison of DRAFT-8
- [x] #3 No automatic run while a human-in-the-loop step waits, without the tools confirmation, or more than once per reload
- [x] #4 If the system no longer exists or its inputs changed after the reload, nothing runs and the page says why
- [x] #5 Browser test: a reload with the toggle on runs and compares; with it off nothing runs
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run panel: 'Re-run after a reload' toggle per system (session storage, off by default); with it on, the trace of each ended run is kept in the page.
2. On the Dev UI's hot reload state: once per reload, wait until getTopology knows the system (bounded attempts), check the last inputs still fit the input schema, the tools were confirmed, then start the run; when it ends, compare it with the trace kept before the reload (compare component takes run A's trace directly).
3. Server: RunObservation.ensureObserved() (idempotent) called by startRun, so a run asked for right after a reload has the Dev UI listener even if the startup observers did not run yet.
4. Browser test with a simulated reload (connection state as the Dev UI client sets it); docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decisions (draft open questions): react to reloads only (no polling to trigger them: a code change reloads on the next request); systems with tools are allowed once their tools were confirmed by a run of the user. The browser test simulates the reload through connection-state (the ui-test fixture sources cannot be modified by QuarkusDevModeTest, src/ui-test); the server-side readiness is exercised by HotReloadDevModeTest (runs started right after reloads). Test covers: on -> re-run and comparison (no difference), off -> nothing, changed inputs -> nothing with the reason, system gone -> nothing with the reason. Both CI configurations BUILD SUCCESS (169/173, 106/127), ui-tests 26/26.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
An opt-in 'Re-run after a reload' per system runs the last inputs again after each hot reload, once the system is known again, and compares the new run with the one kept in the page before the reload; it does not run when the system is gone, its inputs changed or its tools were never confirmed, and says why. Runs started right after a reload now always get the Dev UI listener. Verified with a browser test of all four cases and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
