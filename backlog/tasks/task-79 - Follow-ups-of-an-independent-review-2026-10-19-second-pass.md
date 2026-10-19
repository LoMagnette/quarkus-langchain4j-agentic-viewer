---
id: TASK-79
title: 'Follow-ups of an independent review (2026-10-19, second pass)'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 11:05'
updated_date: '2026-10-19 11:18'
labels:
  - review
  - deployment
dependencies: []
priority: medium
type: bug
ordinal: 135000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
A second review pass on the TASK-78 work found two remaining issues.
1. With quarkus-langchain4j detection (TASK-78.5), the AI metadata of an agent method is matched to the upstream @ToolBox / @McpToolBox / @Skills methods by name only (AgenticAnnotationScanner.forMethod, after stripping the signature of an overloaded id): two overloaded @Agent methods with different annotations can show each other's tools, MCP clients or skills.
2. The parent pom compiles with maven.compiler.source/target 21: on a newer JDK javac warns "location of system modules is not set in conjunction with -source 21" and the code could use APIs newer than Java 21. --release 21 prevents it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Overloaded AI agent methods each get the tool box, MCP tool box and skills of their own annotations when the metadata comes from quarkus-langchain4j (matched by full parameter signature), with a test of two overloads carrying different annotations
- [x] #2 The build compiles with maven.compiler.release=21 (no source/target), without the system modules warning, in both CI configurations
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. forMethod matches an upstream method to a descriptor by the scanner's own id rule: name, or name(parameter types) for an overloaded method (isOverloaded / signature), instead of the bare name. Test: two overloads with different @ToolBox / @McpToolBox / @Skills, listed upstream in the other order.
2. Parent pom: maven.compiler.release=21 in place of source/target; check the javac warning is gone in both builds.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Both findings reproduced: UpstreamSelectionTest.overloadsKeepTheirOwnToolBoxesAndSkills fails before the fix (the first overload got 'auditing', the second overload's skills) and passes after; the builds on JDK 25 printed the system modules warning 6 times each. Validation: mvn -o clean install (deployment 185, integration-tests 106, ITs 6 / 1 skipped, 0 warnings), -Ppatterns (194 / 1 skipped, 139, 6 / 1 skipped, 0 warnings), UI tests 30/30.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Overloaded AI agent methods now get the tool box, MCP tool box and skills of their own annotations when the metadata comes from quarkus-langchain4j: the upstream methods are matched by the scanner's id (full parameter signature for an overload), covered by a test that failed before. The build compiles with maven.compiler.release=21, so a newer JDK running Maven can no longer compile against post-21 APIs; the system modules warning is gone. Verified with both CI builds and the 30 UI tests.
<!-- SECTION:FINAL_SUMMARY:END -->
