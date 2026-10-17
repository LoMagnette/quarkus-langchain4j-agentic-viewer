---
id: TASK-16
title: >-
  U5: Apply AgentListener CDI beans to annotation-built agents
  (quarkus-langchain4j-agentic)
status: To Do
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-17 11:55'
labels:
  - upstream
  - quarkus-langchain4j
milestone: m-7
dependencies:
  - TASK-9
documentation:
  - spec-breakdown.md
priority: high
type: task
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: U5 (unblocks T2.4 if the T0.4 spike finds no existing hook). One of the two most important upstream changes.

If quarkus-langchain4j-agentic has no hook to add listeners to agents it creates from annotations, propose that it applies `AgentListener` CDI beans (or a build item) to every such agent. This removes the need for a listener-injection workaround. Skip this task if T0.4 finds an existing hook.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 If T0.4 found no existing hook: a PR to quarkus-langchain4j applies AgentListener CDI beans or a build item to every agent created from annotations
- [ ] #2 The PR includes a test showing a CDI AgentListener receives callbacks for a nested declarative workflow
- [ ] #3 The PR outcome is recorded here, or the task is closed as not needed with a link to the T0.4 finding
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Input from TASK-67 (doc-1): the requested behaviour (AgentListener CDI beans applied to annotation-built agents) appears to exist already in quarkus-langchain4j-agentic 1.14.1. Close as not needed once TASK-9 confirms it.

TASK-9 result (doc-3): the existing CDI AgentListener support reaches AI agents only (no workflow nodes, no scope lifecycle, no HITL). Root registration via the internal InternalAgent API works as a workaround. Refined proposal: a public hook to attach an inherited listener to the root agents the extension builds, or apply CDI listeners with inheritedBySubagents() = true at the root.

Oct 17, 2026: proposal drafted in Backlog doc-7 (motivation, current viewer workaround, API sketch, test), grounded in langchain4j-agentic 1.20.2-beta30 / quarkus-langchain4j 1.14.1 sources. Nothing posted: opening the issue/PR upstream waits for the project owner's go.

Oct 17, 2026: parked by the project owner (drafts stay in doc-7).
<!-- SECTION:NOTES:END -->
