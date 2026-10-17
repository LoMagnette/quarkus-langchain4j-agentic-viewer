---
id: TASK-3
title: 'U3: Planner decision callbacks on AgentListener (langchain4j-agentic)'
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
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: U3 (unblocks T4.5, simplifies T3.2).

Today the only way to observe planner decisions is to decorate the `Supplier<Planner>` (PlannerDecorator). That cannot see the candidates a planner considered, which the blackboard view needs (ready candidates vs the one chosen). A callback on `AgentListener` reporting the chosen `Action` and, where relevant, the candidates would make planner observation first-class.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A PR to langchain4j-agentic adds AgentListener callback(s) reporting the Action chosen by a planner
- [ ] #2 The callback exposes the candidate agents considered when the planner has that information (e.g. blackboard)
- [ ] #3 The PR includes a test for at least one built-in planner
- [ ] #4 The PR outcome and target release are recorded in this task
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Oct 17, 2026: proposal drafted in Backlog doc-7 (motivation, current viewer workaround, API sketch, test), grounded in langchain4j-agentic 1.20.2-beta30 / quarkus-langchain4j 1.14.1 sources. Nothing posted: opening the issue/PR upstream waits for the project owner's go.
<!-- SECTION:NOTES:END -->
