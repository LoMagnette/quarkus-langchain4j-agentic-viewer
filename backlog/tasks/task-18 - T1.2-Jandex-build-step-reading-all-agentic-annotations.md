---
id: TASK-18
title: 'T1.2: Jandex build step reading all agentic annotations'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-06 11:31'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-17
  - TASK-8
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.2 (F1.1). Declarative systems are known at build time; the deployment module scans them with Jandex into descriptors. The prototype `DeclarativeAgentScanner` targets stub annotations and the old model, so it is reworked or replaced. Split into subtasks by annotation group.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Descriptors are produced for every declarative sample system
<!-- AC:END -->
