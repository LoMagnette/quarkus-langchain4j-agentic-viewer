---
id: TASK-83
title: 'Flaky CI: mapper item workflow test expects every item to run Upper'
status: In Progress
assignee: []
created_date: '2026-10-20 07:17'
labels: []
dependencies: []
type: bug
ordinal: 139000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CI (patterns build) failed in `InvocationCorrelationTest.theAgentsOfAMapperItemThatIsAWorkflowRunUnderTheirItem`: expected 6 child starts, got 5. langchain4j 1.20.2 saves a workflow planner's execution state in the scope under `__planner_state_<agentId>` of the workflow handler, and the items of a parallel mapper share both the scope and that agent id; an item starting after another has moved on restores its cursor and skips its first agent (item 2 ran Exclaim without Upper). The race is upstream; the test must not depend on it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The test still checks that every child agent of a mapper item runs under its own item with the same item index
- [ ] #2 The test no longer depends on how many agents of each item langchain4j runs; every item has at least one child
- [ ] #3 Default and patterns builds pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace the exact count of child starts with: every item invocation is the parent of at least one child. 2. Comment the upstream cause in the test. 3. Run both builds.
<!-- SECTION:PLAN:END -->
