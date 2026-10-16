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

To try the Dev UI on a real model, start the sample app with the `ollama` profile: the fake is not produced and the
OpenAI extension calls any OpenAI-compatible endpoint, by default a local Ollama (tool-capable model needed):

```bash
mvn quarkus:dev -pl integration-tests -Dquarkus.profile=dev,ollama
# overrides: OLLAMA_BASE_URL (default http://localhost:11434/v1; http://host.docker.internal:11434/v1 from the sandbox),
#            OLLAMA_MODEL (default qwen2.5:14b), OPENAI_API_KEY (for a real OpenAI endpoint)
```

The extension never picks a model: a Dev UI run calls the application's agent beans, which use the model the
application configures for each agent.

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
   flags, AI agent details (prompts, `@ModelName`, `@ToolBox` tools), remote agent details (`@A2AClientAgent` server,
   `@McpClientAgent` tool) and input JSON schemas (`InputSchemaGenerator`).
   Resolution rules mirror langchain4j-agentic / quarkus-langchain4j-agentic. Descriptors stay deployment-only.
2. `RootSystemDetector` nests them under root systems (`AgenticRootsBuildItem`): a root is an agent no other agent lists
   as a sub-agent.
3. `deployment/.../topology/TopologyBuilder` turns each root into an `AgenticSystem` (`AgenticSystemsBuildItem`). Node ids:
   `Class#method` for the root, then `/<SubAgentSimpleClassName>` per level (`~2` for a repeated sibling); runtime
   events will be correlated with them.
4. Dev mode only: the systems are recorded into runtime-dev (`AgenticViewerRecorder.getSystems()`), where the
   `AgenticSystemRegistry` bean (registered by a dev-only build step) holds them by id for the JSON-RPC service.

Builder (`AgenticServices.*Builder()`) systems are discovered at runtime in Phase 5.

### Runs (runtime-dev, dev mode only)

`runs/RunManager` starts a run on its own daemon threads (`startRun` returns the run id at once) and emits the
`events/ExecutionEvent` records; `runs/CdiRunInvoker` converts the form JSON to the root method parameters (Jackson) and
calls the root CDI bean inside a fresh request context (request-scoped chat memory must not leak between runs).
Events are delivered by one event thread (callbacks only queue them); `streamRun` replays a run then follows it
(`RunManager.stream`). Over the Dev UI websocket a stream sends a `Void` ack, then one `SubscriptionMessage` per event and
no completion message: the client stops at `RunFinished` / `RunFailed`.
Event payloads are bounded on the event thread (`events/PayloadTruncator`, `quarkus.langchain4j.agentic.dev-ui.max-payload-size`,
default 2048 chars of JSON) and must be plain Java values (maps, lists, strings, numbers): the Dev UI serializer writes
a Jackson `JsonNode` as a bean.
runtime-dev beans only exist in dev mode: tests build `RunManager` directly (`new RunManager(lookup, new CdiRunInvoker())`,
see `integration-tests/.../samples/SampleRunsTest`).
`@HumanInTheLoop` steps of Dev UI runs never call the annotated method: `runs/HumanInTheLoopSteps` (installed with the
listener by `RootListeners.attach`) swaps each step's invoker, through internal langchain4j API, for one that emits
`AwaitingHumanInput` and blocks until `provideHumanInput` (or cancel). Outside Dev UI runs the method runs as usual.
Tests that attach a `DevUiAgentListener` and run TripPlanner must answer (`HumanInputAnswers.answerInTheDevUi`).
Planner decisions: `runs/PlannerDecisions` (also installed by `RootListeners.attach`) replaces each workflow's
`plannerSupplier` (reflection on a final field) so supervisors, `@PlannerSupplier` and pattern planners are wrapped in a
delegating planner that emits `PlannerDecision` per Action; built-in workflow planners (sequence, loop, parallel,
mapper, conditional) are left alone. `runs/AgentTrees` holds the shared walk of langchain4j's internal agent tree.

Topology contributor SPI (`runtime/.../spi/AgenticTopologyContributor`, inert interface + records): implementations
are found with Jandex at build time (dev only), created in runtime-dev (`TopologyContributions`, no-arg constructor)
and matched to PLANNER nodes by the class of their `@PlannerSupplier`; `getContributions(systemId)` serves them with
resolved ids (`<planner node id>::<id>` for contributed nodes) and the graph draws them instead of the hub and
candidates (step numbers then go on the nodes). Sample: `integration-tests/.../samples/DraftReviewTopology`.

Patterns module: there is no Quarkus extension for `langchain4j-agentic-patterns`, apps add it directly. runtime-dev
depends on it as optional; `runtime-dev/.../patterns/*Adapter` classes refer to it and are registered as beans only when
their planner class is present (`AgenticViewerProcessor.patternAdapters`, names only, never loaded otherwise);
`PatternAdapters` (always registered) hands them to the Dev UI listener, and pattern planners' decisions carry
`extras.pattern`. Never reference an adapter class from code that runs without the module. Adapters give an
`Insight` per decision (extras, planned path) and may preview paths (`GoapAdapter` runs the module's own
`GoalOrientedSearchGraph`: `previewGoalPath`, `extras.path` on the first decision). A GOAP planner node is drawn as its
key dependency graph (`goalGraph` in `toGraph`). A P2P planner node is a bipartite agent/key graph (`peerGraph`, laid out left to right
with ELK graph wrapping); `P2PAdapter` reads the private maximum and exit condition by reflection to tell the
termination reason (`extras.termination`).

### Dev UI Integration

`AgenticViewerProcessor.registerDevUI()` registers the card with one page, "Agentic Systems" (`qwc-agentic-systems.js`),
whose link shows the number of systems known at build time. The page gets everything over JSON-RPC
(`AgenticViewerJsonRpcService`: `listSystems`, `getTopology`, `getInputSchema`; unknown ids are JSON-RPC errors) and shows
the selected system with the graph component `qwc-agentic-graph.js` (Cytoscape.js + elkjs from mvnpm; `toGraph()` / `toDataFlowGraph()`
map a topology to nodes/edges; each compound is laid out separately by ELK to keep declaration order;
`highlight(nodeId, state)` for the live view) and the details panel `qwc-agentic-node-details.js`. JSON-RPC method
names are `quarkus-langchain4j-agentic-viewer_<method>`. `DevUIJsonRPCTest` only reads successful results: test error
answers over a raw websocket (see `AgenticSystemsDevModeTest`).
Runs are started from the system view (Run button): `qwc-agentic-run-panel.js` (form `qwc-agentic-run-form.js`, tools
confirmation, live view driving the graph's `highlight` / `setNodeInfo`). Never pass the Dev UI `JsonRpc` proxy through a
Lit binding (lit-html probes `_$litDirective$` and the proxy answers every property): pass a plain adapter object.
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
