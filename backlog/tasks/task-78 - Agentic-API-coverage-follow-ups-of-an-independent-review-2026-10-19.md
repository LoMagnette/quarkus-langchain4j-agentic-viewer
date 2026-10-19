---
id: TASK-78
title: 'Agentic API coverage: follow-ups of an independent review (2026-10-19)'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:44'
updated_date: '2026-10-19 10:46'
labels:
  - review
dependencies: []
priority: high
type: task
ordinal: 129000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
An independent review (2026-10-19) found the extension architecturally sound for Quarkus (runtime/deployment split, conditional dev dependency, recorder, Dev UI SPI, prod and native isolation) but incomplete in its coverage of the LangChain4j Agentic API pinned by the build (langchain4j 1.20.2 / agentic 1.20.2-beta30, quarkus-langchain4j 1.14.2). Each finding was checked against the pinned libraries before being tracked; each is a subtask.

## Constraint
No new access to internal langchain4j hooks for now (owner decision): what needs private fields, reflection on private state or internal classes is left out and noted, as for the postponed drafts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every subtask is Done, or explicitly postponed with the reason recorded
- [x] #2 Both CI configurations (default and -Ppatterns) and the UI tests pass at the end
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All five findings of the 2026-10-19 review checked against the pinned versions and addressed: dev-only discovery (78.1), BDI / debate / voting planners (78.2), behaviour settings in the topology (78.3), model suppliers, skills and suppliers in the AI details (78.4), agent identity from quarkus-langchain4j's detected-agent build items with a dev-mode disagreement log (78.5). Without new internal langchain4j access: what needs it is documented as not shown. Both CI configurations and the 30 UI tests pass.
<!-- SECTION:FINAL_SUMMARY:END -->
