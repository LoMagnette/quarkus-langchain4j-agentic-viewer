---
id: TASK-3
title: 'U3: Planner decision callbacks on AgentListener (langchain4j-agentic)'
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
