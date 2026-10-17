---
id: TASK-10
title: 'T0.5: Spike - walk an AgentInstance tree at runtime'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-17 06:00'
labels:
  - spike
  - runtime-dev
  - builder
  - deferred
milestone: m-5
dependencies:
  - TASK-66
documentation:
  - spec-breakdown.md
priority: low
type: spike
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T0.5. Builder systems only exist at runtime, so their topology must be read from the live `AgentInstance` tree, the way `HtmlReportGenerator.generateTopology` in langchain4j-agentic does. This spike confirms which information (kind, keys, guards, planners) is reachable.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A prototype prints the full tree for one builder system and one declarative system
- [x] #2 A written finding lists which topology-model fields are recoverable at runtime and which are not (e.g. lambda guards)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Prototype AgentTreeSpikeTest: walk declarative and builder systems through the public AgentInstance API only, print every field, check the facts the finding relies on.
2. Cover every kind: sequence, parallel, mapper, loop, conditional, supervisor, custom planner, human step (declarative and builder).
3. Write the finding (Backlog doc-5): field-by-field recoverability against our topology model.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Finding in Backlog doc-5. Prototype AgentTreeSpikeTest (5 tests, prints target/agent-trees.txt): builder (UntypedAgent bean, typed interface bean, per-request router, supervisor/planner/human step) and declarative trees through the public AgentInstance API only; loop/conditional details via as(). Key points: supervisors and custom planners report SEQUENCE (use plannerType first); builder human steps are HUMAN_IN_THE_LOOP, declarative ones NON_AI_AGENT; lambda guards read "<unknown>"; builder workflow roots declare no arguments and output Object; agentId suffixes differ between builder and declarative; AI details only via the annotations of type(). The builder twin of StoryCreator has the declarative shape.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Prototype AgentTreeSpikeTest prints and checks the live trees of builder and declarative systems through the public AgentInstance API; finding in doc-5 lists per topology-model field what is recoverable at runtime (structure, keys, loop settings, planner class, conditions with descriptions, AI details via annotations) and what is not (lambda guard texts, builder-given tools, supervisor settings, origin), plus the kind mapping rules for T5.2. Verified: 5/5 spike tests.
<!-- SECTION:FINAL_SUMMARY:END -->
