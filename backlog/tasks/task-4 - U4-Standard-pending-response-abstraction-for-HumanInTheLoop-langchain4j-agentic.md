---
id: TASK-4
title: >-
  U4: Standard pending-response abstraction for @HumanInTheLoop
  (langchain4j-agentic)
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-17 10:48'
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Oct 17, 2026: proposal drafted in Backlog doc-7 (motivation, current viewer workaround, API sketch, test), grounded in langchain4j-agentic 1.20.2-beta30 / quarkus-langchain4j 1.14.1 sources. Nothing posted: opening the issue/PR upstream waits for the project owner's go.

Finding: langchain4j-agentic 1.20.2-beta30 already has PendingResponse/SuspendedResponse (internal package) and the public AgenticScope.completePendingResponse(id, value) / pendingResponseIds(): external completion exists. U4 narrowed to a listener callback announcing pending responses (prompt, key) and making the response classes public.
<!-- SECTION:NOTES:END -->
