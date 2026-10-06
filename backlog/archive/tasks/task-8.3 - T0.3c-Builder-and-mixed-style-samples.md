---
id: TASK-8.3
title: 'T0.3c: Builder and mixed-style samples'
status: To Do
assignee: []
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 06:26'
labels:
  - samples
  - builder
milestone: m-0
dependencies:
  - TASK-8.1
  - TASK-7.2
documentation:
  - spec-breakdown.md
parent_task_id: TASK-8
priority: high
type: task
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T0.3. Fixtures for builder discovery (Phase 5) and its spikes (T0.5, T0.6). Covers every discovery strategy: CDI-produced systems, systems not exposed as beans, systems rebuilt per request, and mixes of builder and declarative.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A builder system produced as a CDI bean (UntypedAgent) and one exposed as a typed agent interface exist
- [ ] #2 A builder system that is not a bean and one rebuilt per request exist
- [ ] #3 A builder workflow using declarative agent classes as sub-agents, and a declarative workflow using a builder sub-agent, exist
- [ ] #4 A builder twin of one declarative sample exists for topology comparison (T5.2)
- [ ] #5 Each sample has a test that runs it with the fake model
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex. Superseded by TASK-66 (same scope, outside T0.3) and archived.
<!-- SECTION:NOTES:END -->
