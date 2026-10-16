# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Quarkus extension that scans LangChain4j agentic systems at build time and shows them in the Quarkus Dev UI as
interactive graphs (structure and data flow, node details), with live runs to come (Phase 2). It helps developers
understand their agents, workflows, tools and the AgenticScope keys they share.

## Build Commands

```bash
# Build all modules (skipping tests)
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Run integration tests only
mvn verify -pl integration-tests

# Run a single test
mvn test -pl integration-tests -Dtest=AgenticViewerTest

# Include the langchain4j-agentic-patterns samples (always with clean when switching the profile)
mvn clean verify -pl integration-tests -Ppatterns
```

Samples run on a scripted fake chat model (`integration-tests/.../it/fake/ScriptedChatModel`), no API key needed:
declarative samples in `src/main/java/.../it/samples`, patterns samples in `src/patterns/java` (profile `patterns`).

## Architecture

### Module Structure

This is a standard **Quarkus extension** with four modules:

- **runtime/** - Runtime artifacts deployed with the application, also in prod. Only inert topology records
  (`runtime/.../topology`: `AgenticSystem`, `AgentNode`, `Guard`, `PlannerInfo`, `AiAgentDetails`, `ToolInfo`,
  `SystemInput`, `SourceRef`, enums); no beans or behaviour.
- **runtime-dev/** - Dev-mode-only artifact (`quarkus-langchain4j-agentic-viewer-dev`), declared via `conditionalDevDependencies` in `runtime/pom.xml`. Holds everything live: the recorder and the JSON-RPC service. Build steps that reference it must be `onlyIf = IsDevelopment.class`.
- **deployment/** - Build-time processing, only used during compilation: `AgenticViewerProcessor` (`@BuildStep`s),
  the Jandex scan and the topology builder, and the Dev UI web components (`src/main/resources/dev-ui`).
- **integration-tests/** - Test application with sample agents and tools.

### Build-time chain

1. `deployment/.../scan/AgenticAnnotationScanner` reads the agentic annotations with Jandex and produces one
   `AgentDescriptor` per agent method (`AgenticDescriptorsBuildItem`): kind, keys, sub-agents, guards, `@Output`,
   flags, AI agent details (prompts, `@ModelName`, `@ToolBox` tools) and input JSON schemas (`InputSchemaGenerator`).
   Resolution rules mirror langchain4j-agentic / quarkus-langchain4j-agentic. Descriptors stay deployment-only.
2. `RootSystemDetector` nests them under root systems (`AgenticRootsBuildItem`): a root is an agent no other agent lists
   as a sub-agent.
3. `deployment/.../topology/TopologyBuilder` turns each root into an `AgenticSystem` (`AgenticSystemsBuildItem`). Node ids:
   `Class#method` for the root, then `/<SubAgentSimpleClassName>` per level (`~2` for a repeated sibling); runtime
   events will be correlated with them.
4. Dev mode only: the systems are recorded into runtime-dev (`AgenticViewerRecorder.getSystems()`) and passed to the Dev
   UI as the `agenticSystems` build-time data.

Builder (`AgenticServices.*Builder()`) systems are discovered at runtime in Phase 5.

### Dev UI Integration

`AgenticViewerProcessor.registerDevUI()` registers the card with one page, "Agentic Systems" (`qwc-agentic-systems.js`),
whose link shows the number of systems. The page lists the `agenticSystems` build-time data and shows the selected
system with the graph component `qwc-agentic-graph.js` (Cytoscape.js + elkjs from mvnpm; `toGraph()` / `toDataFlowGraph()`
map a topology to nodes/edges; each compound is laid out separately by ELK to keep declaration order;
`highlight(nodeId, state)` for the live view) and the details panel `qwc-agentic-node-details.js`. The JSON-RPC service
(`AgenticViewerJsonRpcService`, runtime-dev) uses method names `quarkus-langchain4j-agentic-viewer_<method>`.
Dev mode does not reload a rebuilt extension: restart it to see Dev UI changes.

### Progress
All progress made on this project is tracked in [this file](AGENT/PROGRESS-SUMMARY.md).

<!-- BACKLOG.MD GUIDELINES START -->
<!-- backlog.md-instructions-version: 1.53.0 -->
<CRITICAL_INSTRUCTION>

## Backlog.md Workflow

This project uses Backlog.md for task and project management.

**At the beginning of each conversation in this project, run `backlog instructions overview` before answering or taking action. Re-read it only if you have not read it yet in the current conversation.**

Use the overview to decide whether to search, read, create, or update Backlog tasks.

Before task lifecycle actions, read the matching detailed guide:
- `backlog instructions task-creation` before creating or splitting tasks
- `backlog instructions task-execution` before planning, changing status or assignee, adding a plan or implementation notes, or implementing task work
- `backlog instructions task-finalization` before checking acceptance criteria, writing final summaries, or moving tasks to terminal statuses

Use `backlog <command> --help` before running unfamiliar commands. Help shows options, fields, and examples.

Do not edit Backlog task, draft, document, decision, or milestone markdown files directly. Use the `backlog` CLI so metadata, relationships, and history stay consistent.

</CRITICAL_INSTRUCTION>
<!-- BACKLOG.MD GUIDELINES END -->
