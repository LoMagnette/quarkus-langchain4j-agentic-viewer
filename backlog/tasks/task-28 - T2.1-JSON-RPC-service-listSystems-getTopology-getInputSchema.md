---
id: TASK-28
title: 'T2.1: JSON-RPC service: listSystems, getTopology, getInputSchema'
status: To Do
assignee: []
created_date: '2026-10-06 06:14'
labels:
  - runtime-dev
  - json-rpc
milestone: m-2
dependencies:
  - TASK-20
  - TASK-25
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.1. The Dev UI reaches everything through one JSON-RPC service backed by `AgenticSystemRegistry`, so builder systems discovered at runtime (Phase 5) show up the same way as declarative ones.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AgenticSystemRegistry holds one entry per root system keyed by a stable id, filled from build-time topology
- [ ] #2 listSystems returns summaries with name, origin, pattern kinds and input count
- [ ] #3 getTopology(systemId) returns the full tree and getInputSchema(systemId) the JSON schema
- [ ] #4 The list and graph pages load their data over JSON-RPC
- [ ] #5 JSON-RPC tests cover the three methods, including an unknown systemId
<!-- AC:END -->
