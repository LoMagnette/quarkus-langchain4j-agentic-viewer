---
id: TASK-49.2
title: 'T4.4b: P2P live activations, invocation counter and termination reason'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:15'
updated_date: '2026-10-16 20:28'
labels:
  - patterns
  - p2p
  - dev-ui
milestone: m-4
dependencies:
  - TASK-49.1
  - TASK-44
documentation:
  - spec-breakdown.md
parent_task_id: TASK-49
priority: medium
type: feature
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T4.4.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Agents light up as their inputs appear in the scope
- [x] #2 An invocation counter is shown against the configured maximum
- [x] #3 The termination reason (stable state, exit condition, max invocations) is shown at the end
- [x] #4 The research sample shows why it stopped
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Adapter insight becomes extras + named agent lists (node ids by the listener): GOAP 'path', P2P 'ready'.
2. P2PAdapter: per decision, invocations (executionState invocationCounter) and maxInvocations (private field, reflection, guarded), ready agents (all inputs in the scope); on done, termination MAX_INVOCATIONS (counter >= max), EXIT_CONDITION (the planner's exit condition holds, private field) or STABLE_STATE (no agent could activate).
3. Live view: key nodes light up when written (ScopeStateChanged), ready agents shown as such, planner node 'invocations n/max', then 'stopped: <reason>'; timeline Done row with the reason.
4. Tests: patterns profile decision extras for ResearchTeam (counter, max, exit condition) plus a max-invocations and a stable-state case; browser check.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
P2PAdapter: invocations from executionState (public), maxInvocations and exit condition from private fields (reflection, guarded), termination MAX_INVOCATIONS / EXIT_CONDITION / STABLE_STATE in P2PPlanner's own order. P2PDecisionsTest (patterns profile): ResearchTeam invocations 1..7,7 against 10, first ready = LiteratureSearcher, stops on EXIT_CONDITION; test-only EndlessDebate stops on MAX_INVOCATIONS at 4/4; LoneSearcher on STABLE_STATE after 1. Live view: activation lighting refined to P2P's rule (inputs present and one written since the agent last ran), otherwise every agent stayed lit once all keys existed. Browser: ResearchTeam lights each agent just before it runs, written keys turn green, planner 'invocations n/10' then 'stopped: exit condition met', timeline 'Done exit condition met'; GOAP and supervisor checks unchanged. Builds: patterns integration 86; default deployment 110, integration-tests 69.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
P2P runs show agents lighting up as they can activate, written keys lighting up, an invocation counter against the maximum and, at the end, why the planner stopped (exit condition, maximum invocations or stable state) on the node and in the timeline. Verified by P2PDecisionsTest (all three reasons) and in headless Chromium on ResearchTeam.
<!-- SECTION:FINAL_SUMMARY:END -->
