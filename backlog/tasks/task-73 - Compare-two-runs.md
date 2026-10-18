---
id: TASK-73
title: Compare two runs
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 10:43'
labels:
  - idea
  - dev-ui
  - runs
dependencies: []
priority: medium
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
After changing a prompt or an agent, the developer wants to know what changed between two runs (outputs, path taken, durations, tokens) without reading two timelines side by side by eye.

## Scope
Select two runs of a system from the history:
- inputs side by side;
- per agent invocation: output, duration, tokens, status, with differences highlighted; invocations present in only one run listed as "only in A / only in B" (supervisors, planners, conditionals take different paths);
- the graph colored by difference (same, changed, only in one run); totals (duration, tokens).
Within one application session (runs do not survive a hot reload today; see DRAFT-9 for keeping one in the browser).

## Feasibility (research 2026-10-18)
- Client-side only: two getRun traces, two buildTimeline() trees (qwc-agentic-timeline.js), values rendered with qwc-agentic-value; a new graph state for the coloring.
- Matching: node id plus the (iteration, itemIndex) path up the parent invocations; when both are null (a supervisor calling the same agent several times) the n-th invocation of that node under the same parent.
- Payloads are truncated (max-payload-size): two values equal up to the cut must show "may differ", never "equal".
- Builder systems found by the transform have call-site ids (Class#method:line): editing their file changes the id, old runs then do not match.

## Open questions
- Text diff of long outputs (word level?) or just changed/unchanged with both values?
- Compare a run with a run kept in the browser across a reload (shared with DRAFT-9)?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Two runs of a system can be selected from the history and compared
- [x] #2 The comparison shows the inputs and, per matched agent invocation, both outputs, durations, tokens and statuses, highlighting what differs; truncated values that agree up to the cut show as possibly different
- [x] #3 Invocations present in only one run are listed as such (different paths of supervisors, planners, conditionals, loops)
- [x] #4 The graph colors nodes as same, changed or only in one run, with total duration and tokens of both runs
- [x] #5 Browser tests cover a sequence with one changed output, a loop with a different number of iterations and a conditional taking another branch
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. qwc-agentic-run-model.js compareRuns(traceA, traceB): invocations keyed by node, iteration and item index under the parents' keys (n-th call when neither), verdict per row (same, maybe when truncated, changed, onlyA, onlyB), per node (strongest), inputs and totals.
2. Run panel history: a compare checkbox per ended run (two at most), 'Compare the 2 runs' fires compare-runs (older as A).
3. qwc-agentic-run-compare.js in a Compare tab of the systems page: totals, inputs (differing ones marked), one row per invocation with both outputs, durations, tokens, statuses; graph colored with new states diff-same, diff-changed, diff-only; Close resets the graph.
4. CompareWorkflow fixture (loop counting on the input, conditional on another) and a browser test; docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decisions (draft open questions): a comparison shows both values with the changed ones highlighted (no word-level text diff); within one application session (DRAFT/TASK-75 may keep a run in the browser). Browser test twoRunsAreComparedInvocationByInvocation: identical runs (no difference, graph same), another loop count and branch (iteration 1 same, iterations 2-3 only in B, Fast only in A, Slow only in B, root changed, two differing inputs, graph colored), one changed output on the same path, totals, truncated values 'maybe' (compareRuns in the page), Close resets the graph. ui-tests 24/24; full builds after TASK-74.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Two runs of a system can be compared from the history: inputs, totals and every agent invocation matched across them (node, iteration, item, parents) with both outputs and what changed, invocations made by one run only, truncated values marked as possibly different, and the graph colored by difference. Verified with a browser test covering same runs, a different loop count, another conditional branch and one changed output.
<!-- SECTION:FINAL_SUMMARY:END -->
