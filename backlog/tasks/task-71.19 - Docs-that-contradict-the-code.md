---
id: TASK-71.19
title: Docs that contradict the code
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-18 04:16'
labels:
  - code-review
  - docs
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: docs
ordinal: 125000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
README.md status says builder systems are not discovered yet (they are: builder-discovery cdi and transform). index.adoc uses {project-version} from an includes/attributes.adoc that does not exist, so the installation snippet shows the placeholder. CLAUDE.md gives qwen2.5:14b as default Ollama model while application.properties uses gemma4:e4b-mlx, and says unknown ids are JSON-RPC errors while listRuns returns [] and cancelRun false for unknown ids. Javadoc errors: BuilderTopology.kind() about human-in-the-loop kinds, SourceRef.callSite example, the misplaced registerDevUI Javadoc in AgenticViewerProcessor, and the ErrorHandlers comment saying every workflow sets its error handler.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 README, index.adoc and CLAUDE.md describe builder discovery, the version, the default model and unknown-id behaviour as the code does
- [x] #2 listRuns and cancelRun behave as documented for unknown ids
- [x] #3 The listed Javadoc comments match the code
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. README: builder discovery (cdi, transform) instead of 'not discovered yet'.
2. index.adoc {project-version}: docs/templates/includes/attributes.adoc filtered by the docs build into pages/includes (maven-resources-plugin 3.4.0).
3. CLAUDE.md: default Ollama model, unknown-id behaviour of listRuns and cancelRun (documented as implemented: empty list, false).
4. Javadoc: BuilderTopology.kind, SourceRef.callSite, registerDevUI, ErrorHandlers/DevUiAgentListener error-handler comments, listRuns/cancelRun unknown ids.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Final verification: mvn -o clean install three rounds in a row in both configurations, all green (deployment 151/155, integration 106/127, failsafe 6 incl. ProdStartupIT); ui-tests 22/22; native container build with NativeArtifactIT and ProdStartupIT green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Docs match the code: README describes builder discovery, index.adoc gets {project-version} from a generated attributes include, CLAUDE.md gives the real default model and unknown-id behaviour (documented on listRuns and cancelRun too), and the stale Javadoc comments are fixed.
<!-- SECTION:FINAL_SUMMARY:END -->
