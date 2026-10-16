# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Quarkus extension that scans LangChain4j agentic code patterns at build time and generates an interactive Mermaid.js diagram in the Quarkus Dev UI. It helps developers visualize their AI agent architecture including agents, tools, workflows, and data flows.

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

- **runtime/** - Runtime artifacts deployed with the application, also in prod. Only inert model records (`AgentNode`, `ToolNode`, `DataFlow`, etc.); no beans or behaviour.
- **runtime-dev/** - Dev-mode-only artifact (`quarkus-langchain4j-agentic-viewer-dev`), declared via `conditionalDevDependencies` in `runtime/pom.xml`. Holds everything live: the recorder and the JSON-RPC service. Build steps that reference it must be `onlyIf = IsDevelopment.class`.
- **deployment/** - Build-time processing, only used during compilation. Contains the `AgenticViewerProcessor` with `@BuildStep` methods and scanners.
- **integration-tests/** - Test application with sample agents and tools.

### Scanning Strategy

The extension uses two complementary scanning approaches:

New scanning (spec-driven, Phase 1): `deployment/.../scan/AgenticAnnotationScanner` produces one `AgentDescriptor` per agent method
(`AgenticDescriptorsBuildItem`); `RootSystemDetector` nests them under root systems (`AgenticRootsBuildItem`);
`deployment/.../topology/TopologyBuilder` turns those into `AgenticSystem` values of the topology model in `runtime/.../topology`
(`AgenticSystemsBuildItem`), recorded into runtime-dev (`AgenticViewerRecorder.getSystems()`) and passed to the Dev UI as the
`agenticSystems` build-time data. Descriptors stay deployment-only. Node ids: `Class#method` for the root, then
`/<SubAgentSimpleClassName>` per level (`~2` for a repeated sibling). The two scanners below belong to the prototype
Mermaid view and are retired by TASK-26.

1. **Jandex (DeclarativeAgentScanner)** - Annotation-based scanning for declarative patterns:
   - `@Agent`, `@SequenceAgent`, `@ParallelAgent`, `@ConditionalAgent`, `@LoopAgent`
   - `@RegisterAiService`, `@Tool`, `@ToolBox`
   - `@SystemMessage`, `@UserMessage`, `@V`, `@MemoryId`
   - `@Output`, `@A2AClientAgent`

2. **ASM (ImperativeAgentScanner)** - Bytecode scanning for imperative builder patterns:
   - `AgenticServices.sequenceBuilder()`, `parallelBuilder()`, etc.
   - Cannot fully analyze runtime-determined structures (lambdas, conditionals)

### Dev UI Integration

The extension registers a Dev UI card via `CardPageBuildItem` in `AgenticViewerProcessor.registerDevUI()`. Build-time data is serialized and passed to the frontend component at `deployment/src/main/resources/dev-ui/qwc-agentic-viewer.js` (Lit + Mermaid.js). The "Agentic Systems" page (`qwc-agentic-systems.js`) lists the `agenticSystems` build-time data and shows the selected
system with the graph component `qwc-agentic-graph.js` (Cytoscape.js + elkjs from mvnpm; `toGraph()` maps a topology to
nodes/edges, each compound is laid out separately by ELK to keep declaration order; `highlight(nodeId, state)` for the live
view). The JSON-RPC service (`AgenticViewerJsonRpcService`, runtime-dev) uses method names
`quarkus-langchain4j-agentic-viewer_<method>`. Dev mode does not reload a rebuilt extension: restart it to see Dev UI changes.

### Key Data Flow

1. `AgenticViewerProcessor.discoverAgents()` invokes scanners → produces `AgenticGraphBuildItem`
2. Graph is recorded at static init via `AgenticViewerRecorder` (dev mode only)
3. Dev UI receives serialized graph as build-time data
4. Frontend renders Mermaid diagram with nodes for agents/tools and edges for compositions/data flows

### Model Records (runtime/model/)

- `AgenticGraph` - Root container for all discovered entities
- `AgentNode` - Represents an agent (type, class, method, parameters, tools)
- `ToolNode` - Represents a tool method
- `WorkflowComposition` - Parent-child relationships (SEQUENCE, PARALLEL, CONDITIONAL, LOOP)
- `DataFlow` - Data passing between agents via `@V` bindings and `outputKey`
- `ImperativePattern` - Detected imperative builder usage locations

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
