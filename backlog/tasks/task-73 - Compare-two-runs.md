---
id: TASK-73
title: Compare two runs
status: To Do
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 06:06'
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
- [ ] #1 Two runs of a system can be selected from the history and compared
- [ ] #2 The comparison shows the inputs and, per matched agent invocation, both outputs, durations, tokens and statuses, highlighting what differs; truncated values that agree up to the cut show as possibly different
- [ ] #3 Invocations present in only one run are listed as such (different paths of supervisors, planners, conditionals, loops)
- [ ] #4 The graph colors nodes as same, changed or only in one run, with total duration and tokens of both runs
- [ ] #5 Browser tests cover a sequence with one changed output, a loop with a different number of iterations and a conditional taking another branch
<!-- AC:END -->
