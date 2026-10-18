---
id: TASK-75
title: Re-run the last inputs after a hot reload (watch mode)
status: To Do
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 09:09'
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
- [ ] #1 A per-system toggle (off by default) re-runs the last inputs once the application reloaded and the system is known again
- [ ] #2 The new run is compared with the run before the reload (kept in the browser), using the comparison of DRAFT-8
- [ ] #3 No automatic run while a human-in-the-loop step waits, without the tools confirmation, or more than once per reload
- [ ] #4 If the system no longer exists or its inputs changed after the reload, nothing runs and the page says why
- [ ] #5 Browser test: a reload with the toggle on runs and compares; with it off nothing runs
<!-- AC:END -->
