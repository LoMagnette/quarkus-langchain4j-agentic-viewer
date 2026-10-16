---
id: TASK-29
title: 'T2.2: RunManager - async runs, run ids, bounded history, cancel'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:48'
labels:
  - runtime-dev
  - run
milestone: m-2
dependencies:
  - TASK-28
  - TASK-27
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.2. Runs can take long (model calls), so they start on a worker thread and are tracked by id. History is bounded to protect memory (default 20 runs per system). Split into core lifecycle and history/cancel.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Concurrent runs are tracked separately
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RunManager with async runs, run ids, a bounded history, traces and best-effort cancel (T2.2a-b).
<!-- SECTION:FINAL_SUMMARY:END -->
