---
id: TASK-53
title: 'T5.2: Runtime TopologyBuilder from an AgentInstance tree'
status: To Do
assignee: []
created_date: '2026-10-06 06:15'
updated_date: '2026-10-06 06:26'
labels:
  - runtime-dev
  - builder
  - topology
  - deferred
milestone: m-5
dependencies:
  - TASK-10
  - TASK-20
  - TASK-66
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.2. Builds on the T0.5 spike. Builder systems must produce the same topology model as declarative ones, so the graph and live view work unchanged. Builder guards are lambdas and can only be labelled "lambda".
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TopologyBuilder converts a live AgentInstance tree into an AgenticSystem
- [ ] #2 The builder twin of a declarative sample produces the same graph as the declarative system (except guard labels)
- [ ] #3 Lambda guards are labelled "lambda"
- [ ] #4 Unit tests cover every node kind built with the builder API
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.
<!-- SECTION:NOTES:END -->
