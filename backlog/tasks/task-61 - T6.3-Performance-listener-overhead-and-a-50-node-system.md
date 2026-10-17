---
id: TASK-61
title: 'T6.3: Performance: listener overhead and a 50-node system'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 14:09'
labels:
  - performance
milestone: m-6
dependencies:
  - TASK-36
documentation:
  - spec-breakdown.md
priority: low
type: task
ordinal: 96000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.3. The listener runs inside every agent invocation and the graph must stay usable for large systems.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Listener overhead per agent invocation is measured and recorded
- [x] #2 Render time and live-view responsiveness for a generated 50-node system are measured and recorded
- [x] #3 Any regression found is tracked as a separate task
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ListenerOverheadBenchmark (integration-tests, run on demand): 50 instant non-AI agents, no listener vs listener on application calls vs Dev UI runs, alternating rounds, best median.
2. LargeSystemUiTest (ui-tests, CI): generated 50-node builder system; render time, long main-thread tasks during render and run, all nodes lit.
3. Record the numbers; track any regression separately.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Results (sandbox, Chromium headless): listener overhead +0.02 us per invocation on application calls, +2.9 us per invocation in Dev UI runs (base 0.46 us); stable over 2 runs. 50 nodes: render ~0.5 s, longest main-thread task ~60 ms while rendering, none during the run; run end ~0.95 s after the Run click for ~0.3 s of agent work; 50 nodes done, 60 timeline rows. No performance regression, so no follow-up task.
Found and fixed on the way: agentAction agents made the builder run form ask for '@AgenticScope' (the scope argument is skipped now, as at build time; BuilderTopologyTest asserts it).
Verification: default build deployment 125 + integration 93, UI tests 6/6.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Measured and recorded: the listener costs ~0.02 us per agent invocation on application calls and ~2.9 us in Dev UI runs; a generated 50-node system renders in ~0.5 s and stays responsive during a live run (LargeSystemUiTest, now in CI). No regression found; fixed a stray @AgenticScope form input found by the large system.
<!-- SECTION:FINAL_SUMMARY:END -->
