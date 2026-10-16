---
id: TASK-18
title: 'T1.2: Jandex build step reading all agentic annotations'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:28'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-17
  - TASK-8
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.2 (F1.1). Declarative systems are known at build time; the deployment module scans them with Jandex into descriptors. The prototype `DeclarativeAgentScanner` targets stub annotations and the old model, so it is reworked or replaced. Split into subtasks by annotation group.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Descriptors are produced for every declarative sample system
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
All four subtasks done (T1.2a-d). Parent AC verified by integration-tests it/samples/SampleDescriptorsTest: indexes the compiled declarative samples with Jandex and runs AgenticAnnotationScanner; checks a root descriptor with the right kind for each of the 9 sample systems, a descriptor for every referenced sub-agent, no unreachable nested agent, and spot checks (loop maxIterations, 3 branch guards, itemsProvider, planner supplier, bank tools). integration-tests now depends on the deployment artifact in test scope; @QuarkusTest classes are unaffected. Validation: mvn verify -pl integration-tests, 34 tests pass. The patterns profile was not rerun.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Declarative agentic systems are fully described at build time by AgenticAnnotationScanner (workflows, supervisor, planner, HITL, guards, @Output, AI agent prompts/model/tools/flags). Verified by deployment unit and build-step tests and by a scan of every declarative sample in integration-tests.
<!-- SECTION:FINAL_SUMMARY:END -->
