---
id: TASK-5
title: 'T0.1: Decide where the extension code lives'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:12'
updated_date: '2026-10-06 06:23'
labels:
  - decision
milestone: m-0
dependencies: []
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.1 (depends on U1).

The code can live inside `quarkus-langchain4j` (agentic extension `deployment` + `runtime-dev`) or stay a Quarkiverse extension (this repo, `io.quarkiverse` groupId). The choice decides module names, groupId, release cadence and how listener attachment to Quarkus-built agents works.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The decision and its consequences (groupId, module names, repo) are recorded as a Backlog decision
- [x] #2 The project owner's hosting decision (separate extension vs inside quarkus-langchain4j) is recorded
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Record the hosting decision made by the project owner (separate Quarkiverse extension, may move upstream later).
2. Align acceptance criteria with that decision (no maintainer agreement needed for a separate extension).
3. Unblock T0.1 from U1 (TASK-1) and defer U1 to drafts.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision (project owner, 2026-10-06): the Dev UI is built as a separate extension in this repo, not inside quarkus-langchain4j. It may move upstream later.

Consequences:
- Repo: this repository (quarkus-langchain4j-agentic-viewer).
- Maven coordinates stay io.quarkiverse.langchain4j:quarkus-langchain4j-agentic-viewer (parent: quarkus-langchain4j-agentic-viewer-parent).
- Modules per spec: runtime, runtime-dev, deployment, integration-tests (T0.2 / TASK-7).
- The extension depends on quarkus-langchain4j-agentic as a regular dependency and cannot rely on its internals; hooks into Quarkus-built agents must go through public APIs or upstream changes (T0.4 spike, U5 / TASK-16).
- U1 (TASK-1) no longer blocks T0.1; it is deferred to drafts for a possible future move upstream.

Recorded as Backlog decision-1 (status accepted). The decision body sections are empty because `backlog decision create` only accepts a title and status; this note carries the rationale.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Hosting decided by the project owner: separate Quarkiverse extension in this repo (io.quarkiverse.langchain4j:quarkus-langchain4j-agentic-viewer, coordinates confirmed in pom.xml and runtime/pom.xml), possibly moving into quarkus-langchain4j later. Recorded as accepted decision-1, with consequences in the implementation notes. Removed the dependency on U1 (TASK-1), which is deferred to drafts. T0.2 (TASK-7) is unblocked.
<!-- SECTION:FINAL_SUMMARY:END -->
