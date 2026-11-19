---
id: TASK-102
title: >-
  Nightly snapshot CI red: langchain4j 1.24 removed ChatMessagesAccess, which
  quarkus-langchain4j uses
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-30 12:02'
updated_date: '2026-11-19 13:28'
labels:
  - ci
  - upstream
dependencies: []
references:
  - .github/workflows/snapshots.yml
  - TASK-65
priority: medium
type: bug
ordinal: 158000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Since the 2026-10-30 run, the nightly job against the langchain4j snapshots (`.github/workflows/snapshots.yml`, TASK-65) fails in both configurations: 28 errors in deployment, every `QuarkusDevModeTest` / `@QuarkusTest` failing to build with `NoClassDefFoundError: dev/langchain4j/agentic/agent/ChatMessagesAccess` from quarkus-langchain4j `AgenticProcessor#generateAgentImplementations` (`generateAgentClass`).

Cause (confirmed by comparing the jars): langchain4j-agentic `1.24.0-beta34-SNAPSHOT` (BOM `1.24.0-SNAPSHOT`, jar `1.24.0-beta34-20261030.091737-2`) removed `ChatMessagesAccess` (`lastUserMessage`, `lastChatRequest`, `lastChatResponse`, `removeLastResponseEvent`) and `AgentInvocationHandler$LastResponse`; it added `DecisionAgentBuilder`, `DecisionAgentInvocationHandler` and `AgentInvocationHandler$ForwardingRegistrar`. `1.23.0-beta33-SNAPSHOT` still has them. The agent classes quarkus-langchain4j generates implement that interface: the pinned 1.14.2 and the newest release 2.0.0.Beta1 (langchain4j 1.21.0) both still use it, and no quarkus-langchain4j snapshot is published. The viewer itself does not use these classes.

Decision (2026-10-30, owner): keep the nightly red and wait for a quarkus-langchain4j release compatible with langchain4j 1.24, rather than adding a stand-in `ChatMessagesAccess` on the snapshots profile. Reconsider the stand-in if that release is long in coming, since meanwhile the nightly can no longer report breaks of the viewer's own hooks (as in TASK-92).

Checked on the snapshot at the time: runtime and runtime-dev compile, and the tests that need no Quarkus build pass (`LangChain4jInternalsTest`, `AgenticAnnotationScannerTest`, `ScannerParityTest`, `PlannerTypeDetectorTest`: 34 tests, -Ppatterns,langchain4j-snapshots). The dev-mode and `@QuarkusTest` suites could not run. The Testcontainers `ServiceConfigurationError` in the same log comes from the OpenWebUI Dev UI card closing at shutdown and is only logged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 quarkus-langchain4j is upgraded to a release whose generated agent classes load with langchain4j 1.24 (root pom `quarkus-langchain4j.version`, with `langchain4j.version` and `quarkus.version` kept in sync as CLAUDE.md describes)
- [ ] #2 The nightly snapshot job is green in both configurations (without-patterns, with-patterns)
- [ ] #3 Any viewer break the new langchain4j brings (LangChain4jInternals hooks, scan, pattern adapters) is fixed, and both regular CI configurations build
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. quarkus-langchain4j 1.15.1 (Quarkus 3.40.1.1, langchain4j 1.23.0) no longer references ChatMessagesAccess (checked in its runtime and deployment jars; 1.14.2 did): upgrade the root pom to it, keeping quarkus.version and langchain4j.version in sync with its parent pom. 2.0.0.Beta4 is also clean but needs Quarkus 4.0.0.CR1: out of scope.
2. Bump the snapshots profile default (1.23.0-SNAPSHOT -> 1.25.0-SNAPSHOT, the current BOM snapshot).
3. Build both regular CI configurations (default, -Ppatterns), fix any break.
4. Build both snapshot configurations against 1.25.0-SNAPSHOT, fix any break of the viewer's hooks.
5. Update docs/CLAUDE.md mentions of the pinned versions.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Upgraded to Quarkus 3.40.1.1 / quarkus-langchain4j 1.15.1 / langchain4j 1.23.0 (1.15.1 jars no longer reference ChatMessagesAccess); snapshots profile default 1.25.0-SNAPSHOT.
Breaks found by the default build, fixed:
- BuilderTopology.agentCode took the first method carrying any dev.langchain4j.agentic.declarative.* annotation, suppliers included, so the AGENT code ref depended on getDeclaredMethods order (BuilderAgentDetailsTest got systemMessage instead of answer). Now only the agent-defining annotations (scanner's @Agent + WORKFLOW_ANNOTATIONS).
- Quarkus 3.40 DevUIBuildTimeDataTest sets its URI in @BeforeEach(ValueRegistry): the anonymous instances created inside tests had none (No URI available). New test helper DevUiBuildTimeData passes the test's ValueRegistry (AgenticSystemsDevModeTest x2, DisabledDevUiDevModeTest).
Seen, harmless: Arc 'programmatic lookup problem' WARN from quarkus-langchain4j-agentic AiAgentCreateInfo.ChatModelInfo.NotNeeded when the fixture ChatModel bean is removed as unused.
Targeted run green: BuilderAgentDetailsTest, BuilderCodeRefsTest, AgenticSystemsDevModeTest, DisabledDevUiDevModeTest (19 tests). Full builds still to run.

- langchain4j 1.23 ParallelMapperPlanner names the per-item output keys after the mapper's agent id (translateAll_<i>), no longer after the sub-agent's output key (translation_<i>). Nothing in the viewer depends on the form: JsonRpcEventSequenceTest.parallelMapper updated. Default build: deployment green (host run), integration-tests 132 with this one failure, now passing (6/6).
<!-- SECTION:NOTES:END -->
