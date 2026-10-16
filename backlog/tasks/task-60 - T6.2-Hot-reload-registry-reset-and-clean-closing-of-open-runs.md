---
id: TASK-60
title: 'T6.2: Hot reload: registry reset and clean closing of open runs'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-16 21:02'
labels:
  - runtime-dev
  - hot-reload
milestone: m-6
dependencies:
  - TASK-29
  - TASK-32
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 95000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.2 (F1.5). A hot reload must clear and rebuild the registry; open runs and streams must not leak threads or reference the old classloader.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A hot reload clears and rebuilds AgenticSystemRegistry
- [x] #2 Open runs are ended with a clear status and their streams completed
- [x] #3 No leaked threads after 10 reloads
- [x] #4 A dev-mode test changes an agent interface and checks the registry updates
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RunManager.shutdown (@PreDestroy: stop or hot reload): every running run ends CANCELLED with RunFailed 'Application stopped or reloaded' (streams complete), waiting human-in-the-loop steps are released, run threads interrupted, the event thread drains then stops (bounded wait).
2. Registry: rebuilt by the restart (new bean, recorder set again at static init); checked by a dev-mode test.
3. Tests: unit test of shutdown with a run blocked in its invoker and a subscribed stream (completes with RunFailed, status CANCELLED, threads gone); dev-mode test with a fixture whose run waits on a human step: modify an agent interface -> getTopology shows the change; 10 reloads each leaving a waiting run -> no agentic-viewer threads left beyond the current app's.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RunManager.shutdown (@PreDestroy, public): open runs end CANCELLED with RunFailed 'Application stopped or reloaded' (streams complete), pending human inputs released, run executor interrupted, event thread drained (2 s bound). Registry: the restart recreates the bean and the recorder is set again at static init. Tests: RunShutdownTest (a run blocked in a 60 s call: stream completes with RunFailed, CANCELLED, viewer threads back to before); HotReloadDevModeTest (fixture with only a human step so runs stay open; changing the step description in the agent interface shows in getTopology after the reload, old runs gone; 10 reloads each leaving an open run -> at most the current app's threads). Mutation check: without @PreDestroy the reload test fails listing ~20 leaked agentic-viewer threads. Builds: default deployment 115 + integration-tests 69; -Ppatterns deployment 118 (1 skipped) + integration-tests 88.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A hot reload rebuilds the registry from the changed code and ends the runs of the old application cleanly (CANCELLED, streams completed, human steps released, no thread left). Verified by RunShutdownTest and HotReloadDevModeTest (10 reloads with open runs), with a mutation check proving the leak test fails without the shutdown.
<!-- SECTION:FINAL_SUMMARY:END -->
