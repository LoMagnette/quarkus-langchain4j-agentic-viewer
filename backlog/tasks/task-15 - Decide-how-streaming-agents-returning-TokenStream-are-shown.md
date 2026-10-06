---
id: TASK-15
title: Decide how streaming agents returning TokenStream are shown
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - decision
  - dev-ui
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
priority: low
type: spike
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Open question from spec-breakdown.md. Agents returning `TokenStream` produce output incrementally. The live view can show tokens as they arrive or only the final output; the choice affects event volume, truncation (F6.3) and the listener design.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A Backlog decision records token-by-token vs final-output display for TokenStream agents, with rationale
- [ ] #2 The decision states the impact on event volume and truncation
<!-- AC:END -->
