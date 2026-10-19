---
id: TASK-78.1
title: Dev-only discovery build steps
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 08:01'
labels:
  - review
  - deployment
dependencies: []
parent_task_id: TASK-78
priority: low
type: chore
ordinal: 130000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Review finding 5. The first three build steps of `AgenticViewerProcessor` (`scanAgenticAnnotations`, `detectRootSystems`, `buildTopologies`) have no `onlyIf`, while every step that uses their result is restricted to dev mode. Quarkus prunes steps nobody consumes, so prod builds do not run them today, but nothing states it: a future consumer outside dev mode would silently bring the scan (and its cost and failure modes) into prod augmentation. Making them explicitly dev-only states the contract of the extension.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The annotation scan, root detection and topology build steps run only in dev mode with the viewer enabled (same onlyIf as the other dev-only steps)
- [x] #2 A prod build (ProdArtifactIT) and a build with the viewer disabled still succeed, and the dev-mode tests still list the systems
- [x] #3 CLAUDE.md describes the discovery steps as dev-only
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. onlyIf = { IsDevelopment, AgenticViewerEnabled } on scanAgenticAnnotations, detectRootSystems, buildTopologies.
2. AgenticDescriptorsBuildStepTest consumed the descriptors in a QuarkusUnitTest (test launch mode, where they are no longer produced): keep its assertions as a plain scanner test over the same fixtures (ScanSummaryTest); real dev-mode builds stay covered by the dev-mode tests.
3. AgenticViewerOutsideDevModeTest also checks that no descriptor, root or system item is produced outside dev mode.
4. CLAUDE.md: discovery steps dev-only. Run deployment tests, then the full builds.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AgenticDescriptorsBuildStepTest consumed the descriptors in a QuarkusUnitTest (test launch mode, where they are no longer produced): its assertions are kept as ScanSummaryTest, over Index.of(the fixtures). AgenticViewerOutsideDevModeTest consumes the three items as OPTIONAL (the build chain refuses a required item without producer) and fails before the change with [descriptors, roots, systems]. No patterns-profile test runs the scan in test mode. Validation: mvn -o clean install BUILD SUCCESS (deployment 171, integration-tests 106, ITs 6 with 1 skipped: ProdArtifactIT passes, DisabledDevUiDevModeTest and AgenticSystemsDevModeTest pass).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The annotation scan, root detection and topology build steps now run only in dev mode with the viewer enabled, like every other step: a prod or test-mode build no longer scans. AgenticViewerOutsideDevModeTest checks it (fails before the change); the former test-mode build-step test became the plain scanner test ScanSummaryTest. CLAUDE.md states the rule. Verified with mvn -o clean install (all green, ProdArtifactIT included).
<!-- SECTION:FINAL_SUMMARY:END -->
