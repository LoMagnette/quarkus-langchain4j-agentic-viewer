---
id: TASK-54
title: 'T5.3: Listener and planner decorator attachment for builder systems'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 07:31'
labels:
  - runtime-dev
  - builder
  - live-view
  - deferred
milestone: m-5
dependencies:
  - TASK-2
  - TASK-52
  - TASK-31
  - TASK-43
documentation:
  - spec-breakdown.md
priority: low
type: feature
ordinal: 87000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T5.3. Depends on U2 (ServiceLoader observer for built systems): builder systems are constructed in user code, so the listener and planner wrapper must be injected when they are built. The T0.7 finding says whether this is possible after `.build()`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 DevUiAgentListener receives callbacks for builder systems discovered via CDI
- [x] #2 Planners created with plannerBuilder are wrapped and emit PlannerDecision events
- [x] #3 The live view works for a produced builder sample system
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Builder systems found through CDI are already built: apply the post-build hooks of the declarative roots (doc-3, doc-4). RootListeners.attach(systemId, root, listener) for one root; RunObservation keeps one Dev UI listener and observes each builder root BuilderSystems finds.
2. Sample BuilderDraftReviewer (plannerBuilder) for planner decisions.
3. Tests: run events on builder node ids, loop iterations, planner decisions; browser check of the live view.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Deferred 2026-10-06 (decision-2): the project owner chose to support declarative agentic systems first; builder API support comes later, as it is more complex.

Done without upstream U2: all three criteria concern systems discovered through CDI, which exist after build; U2 is still needed to observe non-bean systems.
Verification: BuilderRunEventsTest 2/2 (builder StoryCreator: AgentStarted on the builder topology node ids in order, loop iterations 0..2 as declarative; builder DraftReviewer: decisions call Drafter, Reviewer, Drafter, Reviewer, done, output 'A polished draft about solar panels'). Dev UI (scratch dev mode, headless Chromium): both builder samples run with nodes done, loop x3, planner steps 1-4 and Done in the timeline, no page error. Also fixed builder workflow inputs for planners/supervisors/parallel/conditional (keys no agent writes): the builder DraftReviewer form is topic only. Full builds: default deployment 123 + integration 92; -Ppatterns 127 (1 skipped) + 111.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Builder systems discovered through CDI now have the live view: their roots get the Dev UI listener, human-step interception and planner decorator when found, so runs report agent events on the builder node ids and plannerBuilder decisions. Verified with BuilderRunEventsTest, browser runs of both builder samples and full builds in both configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
