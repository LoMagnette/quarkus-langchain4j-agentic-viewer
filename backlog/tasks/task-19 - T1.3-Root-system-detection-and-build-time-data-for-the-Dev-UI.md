---
id: TASK-19
title: 'T1.3: Root system detection and build-time data for the Dev UI'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:31'
labels:
  - deployment
  - discovery
  - dev-ui
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.3 (F1.2). A root system is any agentic interface not referenced as a sub-agent by another; sub-agents appear only inside their parents. Without this the list would show every sub-agent as its own system.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Only root systems are listed; sub-agents appear nested in their parents
- [x] #2 An agent used as a sub-agent of two different roots appears inside both
- [x] #3 A deployment test asserts the root set of the sample app
- [x] #4 Root systems (with their nested sub-agent trees) are produced as a build item; TASK-20 turns them into AgenticSystem values handed to the Dev UI and runtime-dev (decision: AgentDescriptor stays deployment-only)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. RootSystemDetector (deployment/scan, pure): sub-agent references are classes; resolve each class to its agent descriptor (first by id if a class declares several agent methods). Roots = descriptors whose declaring class no descriptor lists as a sub-agent, sorted by id.
2. AgentTree record (agentClass, descriptor or null when the class has no agent method in the index, children); a sub-agent is expanded inside every parent that lists it; recursion stops on a cycle (ancestor already on the path).
3. AgenticRootsBuildItem produced from AgenticDescriptorsBuildItem in AgenticViewerProcessor.
4. Tests: detector unit tests (shared sub-agent under two roots, nesting, unresolved class, cycle), build-step QuarkusUnitTest root set on the fixtures, and the root set of the sample app in integration-tests (scanner + detector on the compiled samples).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision (project owner, Oct 16): AgentDescriptor stays deployment-only; TASK-20 builds AgenticSystem values at build time and hands them to the Dev UI and runtime-dev (AC moved there). Implemented RootSystemDetector (pure): roots = agents whose class no agent lists as a sub-agent, sorted by id; AgentTree(agentClass, descriptor, children) expands a sub-agent inside every parent that lists it; a class with several agent methods resolves to the first by id; a sub-agent class without an agent method is a leaf with a null descriptor; a cycle is cut at the repeated ancestor (agents only in a pure cycle have no root). AgenticRootsBuildItem produced by AgenticViewerProcessor.detectRootSystems. Standalone agents (no parent, no children) are systems of one agent. Tests: RootSystemDetectorTest (6), AgenticDescriptorsBuildStepTest.rootSystems on the fixture app (Writer inside StoryFlow, DraftPlanner and ApprovedStory; Editor inside Polisher and Repeater), integration-tests SampleDescriptorsTest.rootSystemsOfTheSampleApp (exactly the 9 sample systems with nested trees; it runs deployment code from the integration-tests module, since the deployment module cannot see the samples). Validation: deployment 35 tests, integration-tests 35 tests, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Root agentic systems are detected at build time (AgenticRootsBuildItem): only agents no other agent uses are roots, and sub-agents are nested inside every parent that uses them, with cycles cut and unresolved classes kept as leaves. Verified by detector unit tests, the fixture build step, and the exact root set of the sample app.
<!-- SECTION:FINAL_SUMMARY:END -->
