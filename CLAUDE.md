# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Quarkus extension adding an "Agentic Systems" page to the Dev UI for LangChain4j agentic systems: it finds the
declarative systems at build time and shows them as interactive graphs (structure, data flow, node details), runs them
from the Dev UI with live highlighting, a timeline, the AgenticScope state and tool calls, answers `@HumanInTheLoop`
steps, shows supervisor and planner decisions step by step, and has dedicated views for the GOAP, peer-to-peer and
blackboard planners of `langchain4j-agentic-patterns`. Dev mode only: nothing of it reaches prod or native builds.
User documentation: `docs/modules/ROOT/pages/index.adoc`.

## Build Commands

```bash
# Build all modules (skipping tests)
mvn clean install -DskipTests

# Build with tests (CI configuration 1)
mvn clean install

# Everything with the patterns module (CI configuration 2, .github/workflows/build.yml); always clean when switching
mvn clean install -Ppatterns

# Browser tests of the Dev UI page (Playwright, downloads Chromium on first use); LargeSystemUiTest measures a 50-node system
# and a 10000-event run (budgets overridable on a slow machine: -Dui-budget.render|long-run|task=<ms>)
mvn test -pl deployment -Pui-tests '-Dtest=AgenticSystemsPageUiTest,LargeSystemUiTest'

# Versions: root pom properties quarkus.version, quarkus-langchain4j.version and langchain4j.version (the langchain4j
# that quarkus-langchain4j brings: read <langchain4j.version> in its quarkus-langchain4j-parent pom when upgrading)

# Against the langchain4j snapshots (nightly CI, .github/workflows/snapshots.yml): the langchain4j BOM is imported first
# at ${langchain4j.version} (the version quarkus-langchain4j brings; keep in sync), the profile sets the snapshot
mvn -U clean install -Ppatterns,langchain4j-snapshots

# Listener overhead per agent invocation (not part of the build)
mvn test -pl integration-tests -Dtest=ListenerOverheadBenchmark

# Integration tests only, or a single test
mvn verify -pl integration-tests
mvn test -pl integration-tests -Dtest=SampleRunsTest

# Sample app in dev mode (scripted fake model, no API key): http://localhost:8080/q/dev-ui -> Agentic Systems
mvn quarkus:dev -pl integration-tests              # add -Ppatterns for the pattern samples

# Native build of the sample app, then NativeArtifactIT checks it has no Dev UI code (local GraalVM/Mandrel, or add
# -Dquarkus.native.container-build=true); ProdArtifactIT does the same on the JVM package in every build. The sample app
# initializes three quarkus-langchain4j JSON holders at run time for this (application.properties)
mvn install -DskipTests
mvn verify -pl integration-tests -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false \
    -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=NativeArtifactIT

# Config reference of the docs (docs/modules/ROOT/pages/includes), generated from the config roots
mvn install -pl docs
```

Samples run on a scripted fake chat model (`integration-tests/.../it/fake/ScriptedChatModel`): declarative samples in
`src/main/java/.../it/samples`, builder (`AgenticServices.*Builder()`) and mixed samples in `src/main/java/.../it/builder`
(the produced ones are listed), patterns samples in `src/patterns/java` (profile `patterns`). Under Quarkus, a builder
system needs its own agents, built from annotated interfaces with the app's chat model (`it.builder.Agents`): a
langchain4j agent has one parent, so a builder given the agent beans of declarative systems re-parents and renames them
and breaks those systems; a non-AI agent class (`@Agent` method on a class) must be an unremovable bean. To try a real model, use
the `ollama` profile: the fake is not produced and the OpenAI extension calls any OpenAI-compatible endpoint:

```bash
mvn quarkus:dev -pl integration-tests -Dquarkus.profile=dev,ollama
# overrides: OLLAMA_BASE_URL (default http://localhost:11434/v1; http://host.docker.internal:11434/v1 from the sandbox),
#            OLLAMA_MODEL (default gemma4:e4b-mlx in application.properties, needs tool support),
#            OPENAI_API_KEY (for a real OpenAI endpoint)
```

The extension never picks a model: a Dev UI run calls the application's agent beans, which use the model the
application configures for each agent.

## Architecture

### Modules

- **runtime/** - Deployed with the application, also in prod: only inert types. `topology` (records: `AgenticSystem`,
  `AgentNode`, `Guard`, `PlannerInfo`, `AiAgentDetails`, `RemoteAgentDetails`, `ToolInfo`, `SystemInput`, `SourceRef`,
  enums), `spi` (`AgenticTopologyContributor` + records) and `config` (`AgenticViewerRuntimeConfig`). No beans or
  behaviour; `ProdArtifactIT` checks that nothing else reaches the prod artifact.
- **runtime-dev/** - Dev-mode-only artifact (`quarkus-langchain4j-agentic-viewer-dev`), declared via
  `conditionalDevDependencies` in `runtime/pom.xml`: recorder, registry, JSON-RPC service, runs, listener, pattern
  adapters. Build steps that reference it run `onlyIf = { IsDevelopment.class, AgenticViewerEnabled.class }`.
- **deployment/** - Build steps (`AgenticViewerProcessor`), Jandex scan, topology builder, planner type detection, build
  config, and the Dev UI web components (`src/main/resources/dev-ui`).
- **integration-tests/** - Sample app (samples, fake model, test-only systems in `src/test`) and its tests.
- **docs/** - Antora documentation page and the generated config reference.

### Build-time chain

1. `deployment/.../scan/AgenticAnnotationScanner` reads the agentic annotations with Jandex and produces one
   `AgentDescriptor` per agent method (`AgenticDescriptorsBuildItem`): kind, keys, sub-agents, guards, `@Output`,
   flags, AI agent details (prompts, `@ModelName`, `@ToolBox` tools), remote agent details (`@A2AClientAgent` server,
   `@McpClientAgent` tool) and input JSON schemas (`InputSchemaGenerator`).
   Resolution rules mirror langchain4j-agentic / quarkus-langchain4j-agentic. Descriptors stay deployment-only.
2. `RootSystemDetector` nests them under root systems (`AgenticRootsBuildItem`): a root is an agent no other agent lists
   as a sub-agent.
3. `deployment/.../topology/TopologyBuilder` turns each root into an `AgenticSystem` (`AgenticSystemsBuildItem`). Node ids:
   `Class#method` for the root, then `/<SubAgentSimpleClassName>` per level (`~2` for a repeated sibling); run events
   are correlated with them. The rule is `runtime/.../topology/NodeIds`, shared by the build-time topology,
   `BuilderTopology` and the listener (keep it the only one). `PlannerTypeDetector` reads the `@PlannerSupplier` bytecode (ASM) to fill
   `PlannerInfo.plannerClass` / `patternType` (GOAP, P2P, BLACKBOARD, CUSTOM).
4. Dev mode only: the systems are recorded into runtime-dev (`AgenticViewerRecorder`), where the `AgenticSystemRegistry`
   bean holds them by id; the application's topology contributors are recorded by class name too.

Builder (`AgenticServices.*Builder()`) systems produced as CDI beans are listed too (T5.1, `builder-discovery=cdi`):
`deployment/.../builder/BuilderSystemBeans` picks the producer beans whose type is an agent or whose body calls an
agentic `build()` (ASM), keeps them unremovable and records their bean ids; `runtime-dev/.../builder/BuilderSystems`
creates them when the Dev UI first lists the systems (`AgenticSystemRegistry` loads them lazily; startup code uses
`buildTimeSystems()` so no bean is created early) and keeps those without a parent. Runs (T5.6): `CdiRunInvoker` invokes an `UntypedAgent` root with a map of the form values
(converted to the input types), a typed builder interface through its one method; the form schema comes from
`RuntimeInputSchema` (same rules as the build-time `InputSchemaGenerator`, type tables shared in
`runtime/.../topology/InputTypes`, `RuntimeInputSchemaTest` and `NullableParityTest` check parity).
Live view (T5.3): `BuilderSystems` hands each root found to `RunObservation.observe`, which attaches the same Dev UI
listener as for the declarative roots (`RootListeners.attach(systemId, root, listener)`: listener, human steps,
planner decorator); the builder node ids follow the listener's id rule.
`builder-discovery=transform` (T5.4) adds the systems that are no beans: `deployment/.../builder/BuildCallTransformer`
rewrites the application classes calling an agentic `build()` (dev mode only, `BytecodeTransformerBuildItem`) to hand
each result to `runtime-dev/.../builder/BuiltSystems.register(result, "Class#method:line")`, which keeps the latest
instance per call site; the registry picks new or rebuilt ones up on each listing (`BuilderSystems.built()`: roots
only, not the CDI producers' own builds), latest instance per call site and shape, held strongly (T5.5a). Origin MIXED
(T5.5b, owner's rule): a builder system using an agent class of a declarative system (`AgenticViewerRecorder`
`getDeclarativeAgents`, its node gives the build-time details), or a declarative system whose agent bean injects a
builder system (`deployment/.../builder/MixedSystems`). Their topology comes from the live agent tree: `runtime-dev/.../builder/BuilderTopology` builds the same model from the public `AgentInstance` API (loops
and conditions through `as()`, AI details from the annotations of `type()`), with the node id rule of the run events
(simple name of each agent's type); findings in Backlog doc-5 (tree) and doc-6 (discovery).

### Configuration

Config roots under `quarkus.langchain4j.agentic.dev-ui`: `enabled` and `builder-discovery` (build time, `deployment/.../AgenticViewerBuildConfig`,
checked by every dev-only build step through `AgenticViewerEnabled`), `history-size` and `max-payload-size` (run time,
`runtime/.../config/AgenticViewerRuntimeConfig`, read by `RunManager`). The extension processor runs on runtime and
deployment so they are registered and documented.

### Runs (runtime-dev, dev mode only)

`runs/RunManager` starts a run on its own daemon threads (`startRun` returns the run id at once) and emits the
`events/ExecutionEvent` records; `runs/CdiRunInvoker` converts the form JSON to the root method parameters (Jackson) and
calls the root CDI bean inside a fresh request context (request-scoped chat memory must not leak between runs).
Events are delivered by one event thread (callbacks only queue them); `streamRun` replays a run then follows it
(`RunManager.stream`). Over the Dev UI websocket a stream sends a `Void` ack, then one `SubscriptionMessage` per event and
no completion message: the client stops at `RunFinished` / `RunFailed`. History is bounded per system.
Event payloads are bounded on the event thread (`events/PayloadTruncator`) and must be plain Java values (maps, lists,
strings, numbers): the Dev UI serializer writes a Jackson `JsonNode` as a bean.
`RunManager.shutdown` (@PreDestroy, also on hot reload) ends open runs CANCELLED, completes their streams, releases
waiting human steps and stops its threads; the registry is rebuilt by the restart.

At dev-mode startup `RunObservation` calls `RootListeners.attach`, which installs once per root agent instance (a
`runs/DevUiListeners` group serves every Dev UI listener attached to it, each handling the runs of its own manager;
a listener of a shut down manager leaves) on each root agent bean (builder roots: when found, T5.3):
- `DevUiAgentListener` through the internal `InternalAgent.registerInheritedParentListener` (CDI listeners only reach AI
  agents): turns callbacks into events, correlating them to node ids (parent invocation + topology, loop counter,
  mapper item suffix) and observing `@ErrorHandler`s (`ErrorHandlers`, wraps the scope's handler).
- `HumanInTheLoopSteps`: swaps each `@HumanInTheLoop` step's invoker so that, in a Dev UI run, the annotated method is
  not called: `AwaitingHumanInput` is emitted and the step waits for `provideHumanInput` (or cancel).
- `PlannerDecisions`: replaces each workflow's `plannerSupplier` (reflection on a final field) to wrap supervisors,
  `@PlannerSupplier` and pattern planners in a delegating planner emitting `PlannerDecision` per Action; built-in
  workflow planners (sequence, loop, parallel, mapper, conditional) are left alone.
`runs/AgentTrees` holds the shared walk of langchain4j's internal agent tree. All of these hooks use internal
langchain4j API (no public one yet: upstream U3, U4, U5): they are isolated and guarded, so an incompatible upgrade
disables the live information they give, never the application. Every internal class, field and method used is listed
in `runs/LangChain4jInternals` (the hooks take the names from it; add new ones there): `LangChain4jInternalsTest` fails
on an incompatible library on the build class path, and dev mode logs the missing parts at startup.

Patterns module: there is no Quarkus extension for `langchain4j-agentic-patterns`, apps add it directly. runtime-dev
depends on it as optional; `runtime-dev/.../patterns/*Adapter` classes refer to it and are registered as beans only when
their planner class is present (`AgenticViewerProcessor.patternAdapters`, names only, never loaded otherwise);
`PatternAdapters` (always registered) hands them to the listener. Never reference an adapter class from code that runs
without the module. Per decision an adapter gives an `Insight` (extras + named agent lists mapped to node ids):
`GoapAdapter` runs the module's own `GoalOrientedSearchGraph` (`previewGoalPath`, `extras.path`); `P2PAdapter` tells
invocations, ready agents and the termination reason; `BlackboardAdapter` the candidates, strategy, goal status and
termination (private planner state read by reflection).

Topology contributor SPI (`runtime/.../spi/AgenticTopologyContributor`): implementations are found with Jandex at build
time, created in runtime-dev (`TopologyContributions`, no-arg constructor) and matched to PLANNER nodes by the class of
their `@PlannerSupplier`; `getContributions(systemId)` serves them with resolved ids (`<planner node id>::<id>`).
Sample: `integration-tests/.../samples/DraftReviewTopology`.

### Dev UI

`AgenticViewerProcessor.registerDevUI()` registers the card with one page, "Agentic Systems" (`qwc-agentic-systems.js`),
whose link shows the number of systems known at build time. Everything comes over JSON-RPC
(`AgenticViewerJsonRpcService`: `listSystems`, `getTopology`, `getContributions`, `getInputSchema`, `previewGoalPath`,
`startRun`, `streamRun`, `listRuns`, `getRun`, `cancelRun`, `provideHumanInput`; an unknown system or run id is a
JSON-RPC error, except `listRuns` (empty list: no run kept) and `cancelRun` (false: nothing running to cancel));
method names are `quarkus-langchain4j-agentic-viewer_<method>`. Components (Lit + Vaadin, served by the Dev UI):
- `qwc-agentic-graph.js`: Cytoscape.js + elkjs (mvnpm). `toGraph()` / `toDataFlowGraph()` map a topology to
  nodes/edges (pattern planners: `goalGraph`, `peerGraph`, `boardGraph`; contributions replace the candidates); each
  compound is laid out separately by ELK to keep declaration order. The live view goes through `highlight`,
  `setNodeInfo` and `setPath`, kept in a run overlay re-applied after a redraw (view or theme switch).
- `qwc-agentic-run-panel.js` (form `qwc-agentic-run-form.js`, tools confirmation, human input box, history and
  replay; publishes the run's events to the tabs at most once per frame), `qwc-agentic-run-model.js` (`LiveRun` turning
  events into graph states, and the helpers shared by the components), `qwc-agentic-timeline.js` (rows tagged with their node kind, agent
  rows expand to inputs/output), `qwc-agentic-output.js` (Output tab), `qwc-agentic-scope-state.js`,
  `qwc-agentic-node-details.js`; `qwc-agentic-value.js` renders run values for reading (text as text, records as
  key/value lists).
Never pass the Dev UI `JsonRpc` proxy through a Lit binding (lit-html probes `_$litDirective$` and the proxy answers
every property): pass a plain adapter object (`rpc` in `qwc-agentic-systems.js`).
Dev mode does not reload a rebuilt extension: restart it to see Dev UI changes.

### Testing

- runtime-dev beans only exist in dev mode: `@QuarkusTest`s build `RunManager` directly
  (`new RunManager(lookup, new CdiRunInvoker())`), attach `new DevUiAgentListener(runManager)` with
  `RootListeners.attach`, and call the JSON-RPC service class directly (see `integration-tests/.../samples`). Tests
  running TripPlanner with a Dev UI listener must answer its human step (`HumanInputAnswers.answerInTheDevUi`).
- Dev-mode tests (`QuarkusDevModeTest`, deployment) cover JSON-RPC, config and hot reload. `DevUIJsonRPCTest` only
  reads successful results: test error answers over a raw websocket (see `AgenticSystemsDevModeTest`). Fixture apps
  need a `ChatModel` bean even without AI agents; without a model provider their AI agents fail at bean creation (logged).
- The build validation of quarkus-langchain4j-agentic rejects a sub-agent parameter that only the root provides when the
  sub-agent is a `@HumanInTheLoop` step: make it the output of another agent.
- Browser tests of the Dev UI page: `deployment/src/ui-test` (profile `ui-tests`), Playwright Java driven from a
  `QuarkusDevModeTest` (quarkus-playwright only targets `@QuarkusTest`, which has no Dev UI). Its fixture app has the
  OpenAI extension (disabled) to wire its slow echo model into the AI agents. Playwright downloads Chromium on first
  use. Reach the components through shadow-piercing CSS locators; the graph exposes `layoutDone`, `stateOf(id)`,
  `nodeInfo(id)` and `renderedNodes()` for checks. A `vaadin-dialog` element is never visible: wait for it attached.

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
