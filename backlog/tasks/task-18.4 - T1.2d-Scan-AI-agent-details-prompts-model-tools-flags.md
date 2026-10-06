---
id: TASK-18.4
title: 'T1.2d: Scan AI agent details: prompts, model, tools, flags'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (feeds F2.5 node details). The details panel shows prompts, model name, tools and optional/async flags; these come from annotations on the agent method and class.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Descriptors record @SystemMessage and @UserMessage templates, description and the configured model name when present
- [ ] #2 Tools reachable from each AI agent are recorded with their names
- [ ] #3 optional and async flags are recorded
- [ ] #4 A deployment test checks all these fields for one AI agent fixture
<!-- AC:END -->
