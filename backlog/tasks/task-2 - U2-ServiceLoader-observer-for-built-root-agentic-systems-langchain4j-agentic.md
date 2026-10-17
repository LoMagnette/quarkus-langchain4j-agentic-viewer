---
id: TASK-2
title: >-
  U2: ServiceLoader observer for built root agentic systems
  (langchain4j-agentic)
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-17 10:48'
labels:
  - upstream
  - langchain4j-agentic
  - deferred
milestone: m-5
dependencies: []
documentation:
  - spec-breakdown.md
priority: low
type: task
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: U2 (unblocks T5.3, removes the need for T5.4). One of the two most important upstream changes.

Builder-built systems (`AgenticServices.*Builder().build()`) are created in user code, so the Dev UI cannot attach its `AgentListener` or wrap planners unless it hijacks construction with a bytecode transform. A `ServiceLoader`-discovered observer, called by langchain4j-agentic whenever a root agentic system is built, would let the extension register systems, add listeners and wrap `Supplier<Planner>` cleanly. Both projects treat agentic as experimental, so the API surface must stay minimal.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A PR to langchain4j-agentic adds a ServiceLoader-discovered observer invoked once per root agentic system built by any AgenticServices builder
- [ ] #2 The observer can add an AgentListener and wrap the planner supplier before the system is used
- [ ] #3 The PR includes a test showing the observer is called for a nested builder workflow
- [ ] #4 The PR outcome (merged, rejected, pending) and target release are recorded in this task
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Oct 17, 2026: proposal drafted in Backlog doc-7 (motivation, current viewer workaround, API sketch, test), grounded in langchain4j-agentic 1.20.2-beta30 / quarkus-langchain4j 1.14.1 sources. Nothing posted: opening the issue/PR upstream waits for the project owner's go.
<!-- SECTION:NOTES:END -->
