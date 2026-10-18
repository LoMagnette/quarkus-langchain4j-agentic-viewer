---
id: TASK-72
title: 'Open agents, prompts and tools in the IDE'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 09:30'
labels:
  - idea
  - dev-ui
dependencies: []
priority: high
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Node details describe an agent (prompts, tools, planner supplier) but changing it means searching for the class in the IDE. A click from the graph to the code shortens the loop between seeing a problem and fixing it.

## Scope
From node details (and the timeline row of an agent): open in the IDE the agent method (interface or class), its @SystemMessage/@UserMessage fromResource prompt file, each tool method, the @PlannerSupplier / @Output / condition methods; for builder systems the call site (Class#method:line). Only offered when the Dev UI detected an IDE and the target is a source of the application.

## Feasibility (research 2026-10-18)
- Quarkus 3.33 Dev UI: build-time JSON-RPC action devui-ide-interaction_open {fileName, lang, lineNumber} (IdeProcessor) and the component <qui-ide-link fileName lang lineNumber> (import 'qui-ide-link', used by qwc-workspace). fileName: a fully qualified class name (resolved under src/main/java or kotlin) or file://<absolute path> (prompt resources).
- Limits: only registered when an IDE is detected; only dev-mode source directories (classes from dependency jars cannot be opened); qui-ide-link renders plain text outside the application packages unless noCheck; the IDE launches on the machine running Quarkus (not from a container or remote dev).
- Line numbers: Jandex has none; ASM line tables exist only for concrete methods (non-AI agents, tools, static condition methods), not for abstract interface methods. Most robust: resolve the line in dev mode from the source file (find the file like Ide.findSourceFile, search the method declaration), line 1 as fallback.
- Topology change: nodes only carry simple names in their ids; AgentNode needs a source reference (class, method; prompt resource path). ToolInfo already has class and method.

## Open questions
- Resolve lines on the server (a JSON-RPC method reading the source) or open at the class only in a first version?
- How to show targets that cannot be opened (dependency classes): plain text, tooltip?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Node details offer to open the agent's class at its method in the IDE, and its prompt resource file when the prompt comes from a resource
- [x] #2 Tools, the planner supplier, @Output and condition methods listed in node details open at their method
- [x] #3 Builder system nodes open at their call site when known
- [x] #4 Links are only active when the Dev UI detected an IDE and the target is an application source; otherwise the reference stays plain text
- [x] #5 The topology carries each node's source reference (class, method, prompt resource); tests cover declarative, builder and tool references
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime topology: CodeRef (role, class, member, resource, line) and AgentNode.code (with @RecordableConstructor and a 13-arg constructor kept).
2. Scanner: per agent the agent method, @Output, @PlannerSupplier, @ExitCondition, @ActivationCondition methods (in branch order) and fromResource prompts; TopologyBuilder passes them on.
3. BuilderTopology: the agent class (and agent method) of each node, declarative nodes keep their build-time code, the root gets the call site (transform: with its line) or the producer.
4. runtime-dev SourceLocator: finds the file and the declaring line in the dev-mode source and resource directories (public DevConsoleManager.getHotReplacementContext); JSON-RPC getNodeSources(systemId, nodeId) adds the node's tools.
5. Node details 'Code' section: qui-ide-link (fileName file:// URI, lineNumber, noCheck) when the Dev UI detected an IDE and the code was found; plain reference otherwise.
6. Tests: CodeRefsTest, BuilderCodeRefsTest, SourceLocatorTest (incl. the JSON-RPC service), AgenticSystemsDevModeTest.nodeSourcesInTheApplicationSources, browser test; docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision (open question of the draft): lines are resolved on the server from the source file (abstract methods have no line in the bytecode, Jandex none). No internal API: Dev UI action devui-ide-interaction_open through its qui-ide-link component, HotReplacementContext from the public dev-mode SPI. Finding: QuarkusDevModeTest copies sources from src/test/java only, so the ui-test fixture apps have no source directory in dev mode; the browser test stubs getNodeSources for the link part and the dev-mode JSON-RPC test checks real sources (StoryWorkflow.java line 23). Verified: both CI configurations BUILD SUCCESS (deployment 162/166, integration 106/127), ui-tests 23/23. Not verifiable here: the actual IDE launch (no IDE in the sandbox).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Node details list the application code behind each node (agent method, @Output, planner supplier, conditions, prompt resources, tools, builder call site) with its file and line in the application sources, and open it in the IDE when the Dev UI detected one; dependency code stays a plain reference. Verified with new unit, dev-mode and browser tests and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
