---
id: TASK-65
title: 'T6.5: Compatibility CI against langchain4j-agentic snapshots'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:16'
updated_date: '2026-10-17 14:19'
labels:
  - ci
  - testing
milestone: m-6
dependencies:
  - TASK-51
documentation:
  - spec-breakdown.md
priority: low
type: task
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T6.5. langchain4j-agentic and the patterns module are experimental and change often; a nightly job against snapshots catches breakage early.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A nightly CI job builds and tests against langchain4j-agentic and patterns snapshots
- [x] #2 The job is green
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Root pom: import the langchain4j BOM first at ${langchain4j.version} (default = what quarkus-langchain4j brings); profile langchain4j-snapshots sets the snapshot version and the Central snapshot repository.
2. Nightly workflow (schedule + manual) running mvn -B -U clean install -Ppatterns,langchain4j-snapshots.
3. Run the same command locally.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
A BOM imported in a profile loses to the project's imports (first import wins): the langchain4j BOM is imported first in the main dependencyManagement, at a property the profile overrides. Default build resolves 1.20.2-beta30 as before; the profile resolves 1.23.0-beta33-SNAPSHOT (dependency:tree).
Verification: mvn -B -U clean install -Ppatterns,langchain4j-snapshots locally: BUILD SUCCESS, deployment 129 (1 skipped) + integration 112 + ITs, using langchain4j-agentic(-patterns) 1.23.0-beta33-20261016.080604-16; default build after the pom change: 125 + 93. The workflow itself has not run on GitHub (no remote): AC2 checked from the identical local command.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Nightly CI job against the langchain4j snapshots (agentic and patterns), through a langchain4j-snapshots profile overriding the langchain4j BOM imported first. The same command is green locally against 1.23.0-beta33-SNAPSHOT; default builds are unchanged.
<!-- SECTION:FINAL_SUMMARY:END -->
