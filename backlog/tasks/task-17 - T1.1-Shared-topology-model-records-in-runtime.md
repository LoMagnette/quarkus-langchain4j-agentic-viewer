---
id: TASK-17
title: 'T1.1: Shared topology model records in runtime'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - runtime
  - model
milestone: m-1
dependencies:
  - TASK-7
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.1. Build time (deployment), runtime (runtime-dev) and the UI must share one topology model so declarative, builder and mixed systems render identically. It lives in `runtime` because it is the only module present in prod; it must stay inert. Replaces the prototype records (`AgenticGraph`, `WorkflowComposition`, `DataFlow`, `ImperativePattern`).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Records AgenticSystem, AgentNode, Guard and PlannerInfo exist with exactly the fields in the spec Topology model table
- [ ] #2 Enums cover origin (DECLARATIVE, BUILDER, MIXED), node kind (AI_AGENT ... HUMAN_IN_THE_LOOP) and pattern type (GOAP, P2P, BLACKBOARD, CUSTOM)
- [ ] #3 The records serialize to and from JSON without custom code in the UI
- [ ] #4 The records are usable as build-time recorded values
<!-- AC:END -->
