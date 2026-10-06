---
id: TASK-18.1
title: 'T1.2a: Scan workflow annotations, sub-agents and keys'
status: To Do
assignee: []
created_date: '2026-10-06 06:13'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (F1.1). Core structure of declarative systems: the agent and workflow annotations, their sub-agent lists and the AgenticScope keys they read and write.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Descriptors are produced for @Agent, @SequenceAgent, @ParallelAgent, @ParallelMapperAgent, @LoopAgent and @ConditionalAgent
- [ ] #2 Each descriptor records its ordered sub-agents, input keys and outputKey
- [ ] #3 Deployment tests (QuarkusUnitTest) cover each annotation on fixture interfaces
<!-- AC:END -->
