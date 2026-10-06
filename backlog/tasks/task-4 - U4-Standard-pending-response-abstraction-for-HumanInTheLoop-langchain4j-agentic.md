---
id: TASK-4
title: >-
  U4: Standard pending-response abstraction for @HumanInTheLoop
  (langchain4j-agentic)
status: To Do
assignee: []
created_date: '2026-10-06 06:12'
labels:
  - upstream
  - langchain4j-agentic
milestone: m-7
dependencies: []
documentation:
  - spec-breakdown.md
priority: medium
type: task
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: U4 (unblocks T2.11).

`@HumanInTheLoop` currently relies on user code to obtain the human answer, so a tool like the Dev UI cannot answer a pending step. A standard pending-response abstraction (a run is waiting for key X with prompt Y; anyone can complete it) lets the Dev UI pause the live view and call `provideHumanInput`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A PR to langchain4j-agentic introduces a pending-response abstraction for @HumanInTheLoop that exposes the prompt and expected key
- [ ] #2 A pending step can be completed by code other than the application (e.g. a Dev UI service)
- [ ] #3 The PR includes a test completing a HITL step externally
- [ ] #4 The PR outcome and target release are recorded in this task
<!-- AC:END -->
