---
id: TASK-26
title: Retire the prototype Mermaid view and ASM imperative scanner
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:26'
labels:
  - cleanup
  - dev-ui
milestone: m-1
dependencies:
  - TASK-21
  - TASK-22
documentation:
  - spec-breakdown.md
priority: low
type: chore
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Not in the spec task list. The prototype renders Mermaid diagrams (`qwc-agentic-viewer.js`) and detects builder calls with an ASM scanner (`ImperativeAgentScanner`). The spec replaces both: Cytoscape graph (T1.6) and runtime builder discovery (Phase 5). Keeping both views would confuse users and double maintenance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The Mermaid page, ImperativeAgentScanner and the prototype-only model records are removed
- [ ] #2 Any prototype behaviour still needed is covered by a spec task
- [x] #3 The build and all tests pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Delete the prototype: runtime/model records, deployment scanner/DeclarativeAgentScanner and ImperativeAgentScanner, deployment/model/AgenticGraphBuildItem, dev-ui qwc-agentic-viewer.js (Mermaid page) and the unregistered qwc-agentic-card.js; processor steps discoverAgents, recordGraph, the imperative bridge and graph serialization, the prototype build-time data and the Agentic Diagram page; the asm dependency if only the ASM scanner used it.
2. runtime-dev: recorder keeps only the systems; the JSON-RPC service replaces the prototype methods with getSystemCount() so the runtime-dev JSON-RPC plumbing stays tested until T2.1 adds listSystems/getTopology.
3. Tests: deployment AgenticViewerJsonRpcDevModeTest checks getSystemCount; integration-tests AgenticViewerDevModeTest (prototype model unit tests) removed; StoryWorkflow fixture loses its builder workflow (only the ASM scanner test used it).
4. Coverage check: builder detection -> Phase 5 tasks (TASK-52, 56, 57, 66); @RegisterAiService AI services are not agentic systems (out of scope); @A2AClientAgent / @McpClientAgent agents have no spec task: ask the project owner whether to create one.
5. Docs: CLAUDE.md architecture (scanning, data flow, model) rewritten for the new chain.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Removed: runtime/model records (AgenticGraph, AgentNode, ToolNode, DataFlow, WorkflowComposition, ImperativePattern, ...), deployment scanner/DeclarativeAgentScanner and ImperativeAgentScanner, deployment/model/AgenticGraphBuildItem, processor steps discoverAgents / recordGraph / imperative bridge / graph serialization, the prototype build-time data (agentCount, toolCount, hasImperative, imperativeLocations, agenticGraph), the 'Agentic Diagram' Mermaid page (qwc-agentic-viewer.js), the unregistered qwc-agentic-card.js, the asm dependency, the integration-tests prototype model unit tests and the builder workflow of the StoryWorkflow fixture. runtime-dev: the recorder keeps only the systems; the JSON-RPC service now exposes getSystemCount() (keeps the runtime-dev JSON-RPC plumbing tested until T2.1). CLAUDE.md architecture rewritten for the build-time chain. Coverage of prototype behaviour: builder detection -> Phase 5 (TASK-52 CDI discovery, TASK-56 bytecode transform, TASK-57 mixed merge, TASK-66 builder samples); @RegisterAiService AI services are not agentic systems (out of scope); tools as nodes -> tools in the details panel (T1.7); data flows -> T1.8. Gap: @A2AClientAgent (scanned by the prototype) and @McpClientAgent agents have no spec task; they show as unresolved leaves if used as sub-agents. Asked the project owner whether to create one. Validation: mvn clean install (deployment 61 tests, integration-tests 31), integration-tests -Ppatterns 34 tests, all pass; Dev UI checked in headless Chromium (card lists only Agentic Systems with the count, list, graphs, data flow, no page errors).
<!-- SECTION:NOTES:END -->
