---
id: TASK-71
title: Fixes from the full code review of 2026-10-17
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:02'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
milestone: m-6
dependencies: []
priority: high
type: task
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A full review of the project (runtime-dev runs and builder code, deployment build steps, Dev UI components, tests, CI and docs) found correctness bugs, robustness gaps, test and CI weaknesses and docs that contradict the code. Each subtask covers one area and can be delivered on its own; together they bring the extension to release quality (phase 6 hardening).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every subtask is Done or explicitly deferred with a reason
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All 20 subtasks of the 2026-10-17 code review are done, each with tests failing before the fix; three consecutive full builds in both configurations, the browser tests and the native check are green. Follow-up ideas recorded as drafts DRAFT-3..15.
<!-- SECTION:FINAL_SUMMARY:END -->
