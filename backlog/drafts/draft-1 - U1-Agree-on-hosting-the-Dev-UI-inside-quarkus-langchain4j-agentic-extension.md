---
id: DRAFT-1
title: 'U1: Agree on hosting the Dev UI inside quarkus-langchain4j agentic extension'
status: To Do
assignee: []
created_date: '2026-10-06 06:11'
updated_date: '2026-10-06 06:23'
labels:
  - upstream
  - quarkus-langchain4j
milestone: m-7
dependencies: []
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: U1 (unblocks T0.1). See spec-breakdown.md "Upstream work".

The Dev UI could live inside the `quarkus-langchain4j` agentic extension (its `deployment` and new `runtime-dev` modules) or as a separate Quarkiverse extension. Hosting upstream gives direct access to how Quarkus builds annotated agents (needed for listener attachment) and avoids version skew; a separate extension is faster to iterate on. The maintainers must agree before the skeleton is built in its final home.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An issue on quarkus-langchain4j proposes hosting the Dev UI in the agentic extension deployment and runtime-dev modules
- [ ] #2 Maintainers have answered with an accepted or rejected decision, linked from the issue
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06: the project owner chose a separate Quarkiverse extension for now (decision-1, TASK-5). Hosting the Dev UI inside quarkus-langchain4j may be revisited later; this proposal no longer blocks T0.1. Moved to drafts.
<!-- SECTION:NOTES:END -->
