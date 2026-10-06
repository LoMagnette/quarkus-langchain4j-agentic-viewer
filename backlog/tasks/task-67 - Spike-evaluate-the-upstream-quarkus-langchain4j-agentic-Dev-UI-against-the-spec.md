---
id: TASK-67
title: >-
  Spike: evaluate the upstream quarkus-langchain4j-agentic Dev UI against the
  spec
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 09:11'
updated_date: '2026-10-06 09:18'
labels:
  - spike
  - upstream
  - dev-ui
milestone: m-0
dependencies:
  - TASK-8.2
documentation:
  - spec-breakdown.md
priority: high
type: spike
ordinal: 102000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
quarkus-langchain4j-agentic 1.14.1 ships its own Dev UI (AgenticDevUIProcessor: Agents, Topology, Executions and Testing pages, a JSON-RPC service, DevAgentMonitorHolder). It overlaps much of spec-breakdown.md F1-F4 and was unknown when the backlog was written. The project owner chose to evaluate it before continuing with Phase 1, because it decides whether this project builds its own extension, contributes upstream, or only fills gaps.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A Backlog document compares the upstream Dev UI with spec features F1 to F6 (covered, partial, missing), based on running the declarative samples in dev mode
- [x] #2 Upstream bugs or limitations found are listed with a reproduction
- [x] #3 The document ends with a recommendation (own extension, contribute upstream, or a mix) and its impact on the existing backlog
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read the upstream sources: AgenticDevUIProcessor, AgenticJsonRpcService, DevAgentMonitorHolder and the qwc-agents*.js pages (deployment jar resources).
2. Run the sample app in dev mode and exercise every page with headless Chromium, before and after running samples (via the upstream Testing page if it works, otherwise a small dev-only trigger in the sample app).
3. Map each spec feature F1-F6 to covered / partial / missing, with evidence (screenshots, code references).
4. List bugs/limitations with reproductions.
5. Write the comparison and recommendation as a Backlog document.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Comparison, bug list and recommendation written in Backlog doc-1 ("Upstream agentic Dev UI vs spec"). Evidence: all 9 samples run in dev mode through the upstream invokeAgent JSON-RPC (8 succeed; PhraseTranslator fails on its List parameter), StoryCreator run through the Testing page in headless Chromium, every Topology and Execution report rendered and inspected. Sample root interfaces were temporarily made MonitoredAgent for the run, then restored from a backup (diff identical); integration-tests rebuilt and its tests rerun afterwards. Key finding for T0.4/U5: quarkus-langchain4j-agentic already wires CDI AgentListener beans into every agent it builds.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Evaluated the quarkus-langchain4j-agentic 1.14.1 Dev UI against spec F1-F6 by running all declarative samples through it. Upstream covers static structure and post-hoc execution (agent list, topology with loops/guards/data flow, waterfall with iterations and tools, run history); it lacks live highlighting, supervisor/planner and pattern views, HITL, error recovery and a typed run form, and has 8 reproducible bugs. Recommendation in doc-1: complement upstream rather than duplicate it, and push fixes upstream.
<!-- SECTION:FINAL_SUMMARY:END -->
