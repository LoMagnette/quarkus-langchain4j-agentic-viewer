# Quarkus LangChain4j Agentic Dev UI — Specs & Task Breakdown

Oct 6, 2026 · @Loïc Magnette

## Overview

A dev-mode-only Dev UI page that lists every LangChain4j agentic system in a Quarkus app, draws it as a graph, runs it with user-supplied inputs and shows the run live. It covers the declarative API, the builder API and mixes of both, with extra pattern-aware views when `langchain4j-agentic-patterns` is on the classpath.

**In scope**

- Discover all agentic systems, declarative and builder-built.
- Render one topology graph per system, nested patterns included.
- Run a system from the UI with a generated input form.
- Stream execution events live onto the graph and a timeline.
- Pattern-aware views for goal-oriented, peer-to-peer and blackboard planners.

**Out of scope**

- Production monitoring or telemetry (OpenTelemetry and Langfuse integrations already cover it).
- Editing agent definitions from the UI.
- Persisting run history across restarts or hot reloads.
- Replacing the Quarkus Flow Dev UI; the two should coexist.

**Dependencies**

| Artifact | Required | What the extension uses from it |
| --- | --- | --- |
| `quarkus-langchain4j-agentic` (brings `langchain4j-agentic`) | Yes | Agents, workflows, supervisor, `Planner`, `AgentListener`, `AgentMonitor`, `AgenticScope` |
| `langchain4j-agentic-patterns` | Optional, detected | `GoalOrientedPlanner`, `P2PPlanner`, `BlackboardPlanner` and their graph logic |
| Quarkus Dev UI SPI | Yes, deployment only | Card page, JSON-RPC services, build-time data |
| Graph library via mvnpm (Cytoscape.js + ELK layout) | Yes, dev only | Rendering and live highlighting |

**Definition styles to support**

| Style | How it is created | Discovery point |
| --- | --- | --- |
| Declarative | `@SequenceAgent`, `@PlannerAgent` etc. on interfaces; Quarkus registers CDI beans | Build time (Jandex) |
| Builder | `AgenticServices.*Builder().build()` in app code | Runtime only |
| Mixed | Builder workflow using declarative agent classes as sub-agents, or the reverse | Runtime, enriched with build-time metadata |

## Architecture spec

Three modules, so only an inert model module ships in a production build: build-time scanning in `deployment`, everything live in `runtime-dev`, and an almost empty `runtime`.

&#91;embedded content: extension architecture · build time, dev-mode runtime, Dev UI\]

The registry is the hub: declarative topology arrives from the build, builder systems and live events arrive at runtime, and the Dev UI reaches everything through one JSON-RPC service.

**Modules**

| Module | Contents | Present in prod |
| --- | --- | --- |
| `runtime` | Shared model records, optional SPI interfaces (`AgenticTopologyContributor`) | Yes (tiny, no behaviour) |
| `runtime-dev` | `AgenticSystemRegistry`, `DevUiAgentListener`, `PlannerDecorator`, `RunManager`, JSON-RPC service, pattern adapters | No |
| `deployment` | Jandex scan of agentic annotations, build-time topology, Dev UI card and pages, optional bytecode transform | No |

**Core components (runtime-dev)**

- `AgenticSystemRegistry`: one entry per root agentic system, keyed by a stable id; merges build-time topology with runtime instances; cleared on hot reload.
- `TopologyBuilder`: turns a declarative descriptor or a live `AgentInstance` tree into the shared topology model.
- `DevUiAgentListener`: an `AgentListener` with `inheritedBySubagents() = true`; maps callbacks to events and pushes them to a non-blocking broadcaster.
- `PlannerDecorator`: wraps any `Supplier<Planner>` and emits a decision event for each `Action` returned by `firstAction` / `nextAction`.
- `RunManager`: starts runs on a worker thread, tracks run ids, holds a bounded history (default 20 runs per system).
- `PatternAdapter` (one per pattern): optional, loaded only when `langchain4j-agentic-patterns` is present.

**Topology model**

| Type | Fields |
| --- | --- |
| `AgenticSystem` | `id`, `name`, `rootNodeId`, `origin` (DECLARATIVE, BUILDER, MIXED), `sourceRef` (class + method or call site), `inputs` (name, Java type, JSON schema) |
| `AgentNode` | `id`, `name`, `kind` (AI\_AGENT, NON\_AI\_AGENT, SEQUENCE, PARALLEL, PARALLEL\_MAPPER, LOOP, CONDITIONAL, SUPERVISOR, PLANNER, HUMAN\_IN\_THE\_LOOP), `description`, `inputKeys`, `outputKey`, `optional`, `async`, `children` |
| `Guard` | On LOOP and CONDITIONAL nodes: `label` (method name or "lambda"), `maxIterations`, `testExitAtLoopEnd` |
| `PlannerInfo` | On PLANNER nodes: `plannerClass`, `patternType` (GOAP, P2P, BLACKBOARD, CUSTOM), pattern-specific extras |

**Execution event model**

| Event | Source | Key fields |
| --- | --- | --- |
| `RunStarted` / `RunFinished` / `RunFailed` | `RunManager` | `runId`, `systemId`, inputs, output or error, duration |
| `ScopeCreated` / `ScopeDestroyed` | `afterAgenticScopeCreated` / `beforeAgenticScopeDestroyed` | `runId`, `memoryId` |
| `AgentStarted` / `AgentFinished` / `AgentFailed` | `beforeAgentInvocation` / `afterAgentInvocation` / `onAgentInvocationError` | `nodeId`, `invocationId`, `iteration`, inputs, output, tokens, duration |
| `ToolStarted` / `ToolFinished` | `beforeToolExecution` / `afterToolExecution` | tool name, arguments, result |
| `PlannerDecision` | `PlannerDecorator` | `nodeId`, agents selected, `done` flag, pattern extras (GOAP path, P2P activations, blackboard candidates) |
| `ScopeStateChanged` | Derived from agent outputs | key, new value (truncated) |
| `AwaitingHumanInput` | `@HumanInTheLoop` integration | `runId`, prompt, expected key |

**JSON-RPC contract**

| Method | Returns | Notes |
| --- | --- | --- |
| `listSystems()` | `List<AgenticSystemSummary>` | Name, origin, pattern kinds, input count |
| `getTopology(systemId)` | `AgenticSystem` | Full tree |
| `getInputSchema(systemId)` | JSON schema | Drives the run form |
| `startRun(systemId, inputs)` | `runId` | Returns at once; run is async |
| `streamRun(runId)` | `Multi<ExecutionEvent>` | Live subscription, replays events already emitted |
| `listRuns(systemId)` / `getRun(runId)` | Run summaries / full trace | Bounded history |
| `cancelRun(runId)` | boolean | Best effort |
| `provideHumanInput(runId, value)` | boolean | Completes a pending human-in-the-loop step |
| `previewGoalPath(systemId, inputKeys)` | Ordered agent list | Patterns module only |

## Functional specs

Six features, each with the behaviour it must have; F1 to F4 work on core alone, F5 needs the patterns module, F6 is cross-cutting.

### F1. Discovery

1. Declarative systems are found at build time by scanning for `@Agent`, `@SequenceAgent`, `@ParallelAgent`, `@ParallelMapperAgent`, `@LoopAgent`, `@ConditionalAgent`, `@SupervisorAgent`, `@PlannerAgent` and `@HumanInTheLoop`.
2. A root system is any agentic interface not referenced as a sub-agent by another one; sub-agents appear only inside their parents.
3. Builder systems are found at runtime through one of three strategies, chosen by config `quarkus.langchain4j.agentic.dev-ui.builder-discovery`:
   1. `cdi` (default): beans whose type is `UntypedAgent` or implements `AgentInstance`.
   2. `transform`: dev-mode bytecode transform registering every `.build()` result of an `AgenticServices` builder.
   3. `spi`: an upstream registration hook, once it exists.
4. Systems that are built more than once (for example per request) are deduplicated by structure and call site.
5. A hot reload clears and rebuilds the registry.

### F2. Topology graph

1. One graph per root system, with nested patterns drawn as compound nodes (a loop inside a sequence is a box inside a box).
2. Each node kind has its own shape: sequence edges are ordered arrows, parallel children fan out and join, loops show a back-edge labelled with `maxIterations`.
3. Conditional branches show the guard label: the `@ActivationCondition` method name for declarative, "lambda" for builder.
4. Supervisor and generic planner nodes draw their sub-agents as candidates (dashed edges), not as a fixed order.
5. Clicking a node shows its details: description, input keys, output key, prompts for AI agents, model name, tools, optional and async flags.
6. A toggle switches to a data-flow view: agents linked through the `AgenticScope` keys they read and write.

### F3. Run panel

1. The form is generated from the root method's parameters for typed systems, or from the required input keys for `UntypedAgent`.
2. Simple types get matching inputs (text, number, boolean, enum dropdown); records, lists and maps get a JSON editor pre-filled with a skeleton.
3. Inputs are validated against the JSON schema before submit; missing required keys are flagged.
4. Submitting calls `startRun` and switches to the live view.
5. The last inputs used per system are kept for the session, so a run can be repeated in one click.
6. Before the first run of a session, a confirmation lists the tools reachable from the system, because they execute for real.

### F4. Live execution view

1. The graph highlights each node as it starts, finishes or fails, with duration and token count on finished nodes.
2. Loop iterations and parallel-mapper items are shown as counters on the node and as separate rows in the timeline.
3. A waterfall timeline lists every invocation, nested by parent, the way `AgentMonitor` records it.
4. A side panel shows the `AgenticScope` state, updated as keys are written.
5. Tool calls appear under the agent that made them, with arguments and results.
6. Supervisor runs show the path actually taken as solid edges over the dashed candidates.
7. A `@HumanInTheLoop` step pauses the view with an input box that calls `provideHumanInput`.
8. Errors show the exception, the failing agent and whether an `@ErrorHandler` retried or recovered.
9. Past runs can be reopened from a run list and replayed on the graph.

### F5. Patterns support (optional)

Active only when `langchain4j-agentic-patterns` classes are detected at build time; otherwise planner nodes use the generic view from F2.4.

| Pattern | Static view | Live view | Extra |
| --- | --- | --- | --- |
| Goal-oriented (GOAP) | Dependency graph of agents by input and output keys, goal key highlighted | Computed path highlighted, then walked step by step | Path preview from the inputs entered in the run form |
| Peer-to-peer | Bipartite graph of agents and state keys, cycles allowed | Agents lighting up as their inputs appear; invocation counter against the max | Termination reason shown (stable state, exit condition, max invocations) |
| Blackboard | Agents as knowledge sources around the scope | Per step: ready candidates, the one chosen, and the conflict resolution strategy | Goal predicate status shown after each step |
| Custom planner | Generic planner node | Decision events from `PlannerDecorator` | Optional `AgenticTopologyContributor` SPI for user-defined drawing |

### F6. Cross-cutting

1. Everything is dev mode only and disabled with `quarkus.langchain4j.agentic.dev-ui.enabled=false`.
2. Listener callbacks never block: they enqueue and return.
3. Event payloads are truncated (default 2 KB per value) and history is bounded, both configurable.
4. Works on the JVM in dev mode; native and prod builds contain no Dev UI code.

## Task breakdown

51 tasks in seven phases. Phase 0 is spikes that de-risk everything else; the declarative path (phases 1 to 3) ships first, patterns and builder support follow.

&#91;embedded content: phase order and gates · 7 phases\]

Phase 5 can start in parallel with phases 3 and 4 once the run and live view exist and U2 is settled.

### Phase 0. Setup and spikes

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T0.1 | Decide where the code lives (inside `quarkus-langchain4j` or a Quarkiverse extension) | U1 | Maintainers agree in an issue |
| T0.2 | Create the extension skeleton: `runtime`, `runtime-dev`, `deployment`, `integration-tests` | T0.1 | An empty Dev UI card shows in a sample app |
| T0.3 | Build a sample app covering every pattern, declarative, builder and mixed, plus GOAP, P2P and blackboard, on a scripted fake `ChatModel` | T0.2 | All systems run deterministically without an API key |
| T0.4 | Spike: attach a listener to agents that Quarkus builds from annotations | T0.3 | Written finding, plus a prototype receiving `beforeAgentInvocation` for a nested sequence |
| T0.5 | Spike: walk an `AgentInstance` tree at runtime, as `HtmlReportGenerator.generateTopology` does | T0.3 | Prints the full tree for a builder and a declarative system |
| T0.6 | Spike: builder discovery via CDI vs bytecode transform | T0.5 | Finding with coverage and cost of each |
| T0.7 | Spike: identify and wrap a `Supplier<Planner>` (declarative `@PlannerSupplier` and `plannerBuilder`) | T0.3 | Decorated planner emits each `Action` in the sample |
| T0.8 | Spike: graph library with compound nodes and ELK layout inside a Dev UI Lit page via mvnpm | T0.2 | Nested loop-in-sequence renders and highlights a node on demand |
| T0.9 | Align scope with the Quarkus Flow team | none | Agreed split noted in this doc |

### Phase 1. Declarative topology (F1, F2)

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T1.1 | Shared topology model records in `runtime` | T0.2 | Records match the topology model above |
| T1.2 | Jandex build step reading all agentic annotations, sub-agents, keys, guards, `maxIterations`, `@Output` | T1.1 | Descriptors produced for every sample system |
| T1.3 | Root system detection and build-time data handed to the Dev UI | T1.2 | Only roots listed; sub-agents nested |
| T1.4 | `TopologyBuilder` from declarative descriptors | T1.2 | Unit tests per pattern kind |
| T1.5 | Dev UI card and system list page | T1.3 | Lists names, origin, pattern kinds |
| T1.6 | Graph component: shapes per kind, compound nodes, loop back-edges, guard labels | T0.8, T1.4 | Every sample system renders legibly |
| T1.7 | Node details panel: prompts, model, tools, keys, flags | T1.6 | All fields shown for an AI agent |
| T1.8 | Data-flow view toggle based on scope keys | T1.6 | Keys shown as links between agents |
| T1.9 | Input JSON schema generation from root method parameters | T1.2 | Schemas for primitives, enums, records, lists |

### Phase 2. Run and live view, declarative (F3, F4)

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T2.1 | JSON-RPC service: `listSystems`, `getTopology`, `getInputSchema` | T1.4, T1.9 | UI loads data over JSON-RPC |
| T2.2 | `RunManager`: async start, run ids, bounded history, cancel | T2.1 | Concurrent runs tracked separately |
| T2.3 | Deserialize form JSON to parameter types and invoke the CDI bean | T2.2 | Typed sample systems run from a unit test |
| T2.4 | `DevUiAgentListener` attached through the hook from T0.4 | T0.4 | All callbacks received for every sample system |
| T2.5 | Broadcaster and `streamRun` with replay of earlier events | T2.4 | Late subscriber still sees the full run |
| T2.6 | Correlate callbacks to node ids, loop iterations, parallel threads | T2.4 | Parallel and mapper runs map to the right nodes |
| T2.7 | Run form UI with validation and the tools confirmation | T2.1 | Invalid input blocked; confirmation shown once per session |
| T2.8 | Live graph highlighting, durations, tokens, counters | T2.5, T2.6 | Loop of 3 iterations shows counter 3 |
| T2.9 | Waterfall timeline, scope state panel, tool calls | T2.5 | Matches `AgentMonitor` output for the same run |
| T2.10 | Error display, including `@ErrorHandler` retry or recovery | T2.8 | Sample failure shows agent, exception and outcome |
| T2.11 | Human-in-the-loop waiting state and `provideHumanInput` | T2.5, U4 | Sample HITL run completes from the UI |
| T2.12 | Run list and replay on the graph | T2.9 | Past run replays after a page refresh |

### Phase 3. Supervisor and generic planners

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T3.1 | Supervisor: candidates dashed, path taken solid | T2.8 | Bank sample shows exchange, withdraw, credit in order |
| T3.2 | `PlannerDecorator` for declarative and builder planners | T0.7 | Decision events for every planner in the sample |
| T3.3 | Generic planner live view from decision events | T3.2 | Custom planner run is readable step by step |
| T3.4 | `AgenticTopologyContributor` SPI for user-defined planners | T3.3 | Sample custom planner draws its own topology |

### Phase 4. Agentic patterns module (F5)

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T4.1 | Detect `langchain4j-agentic-patterns` at build time; register adapters conditionally | T0.2 | App without the module starts with no errors or extra beans |
| T4.2 | Identify the planner type behind each planner node | T3.2, T4.1 | GOAP, P2P, blackboard and custom tagged correctly |
| T4.3 | GOAP adapter: dependency graph, `previewGoalPath`, live path | T4.2 | Preview matches the path the run takes |
| T4.4 | P2P adapter: agent and key graph, activations, termination reason | T4.2 | Research sample shows why it stopped |
| T4.5 | Blackboard adapter: candidates, choice, strategy, goal status | T4.2, U3 | Each step shows ready candidates and the one picked |
| T4.6 | Test matrix with and without the patterns module | T4.3, T4.4, T4.5 | CI runs both |

### Phase 5. Builder API (F1.3)

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T5.1 | CDI discovery of `UntypedAgent` and `AgentInstance` beans | T0.6 | Produced builder systems listed |
| T5.2 | Runtime `TopologyBuilder` from an `AgentInstance` tree | T0.5 | Same graph as the declarative twin of a system |
| T5.3 | Listener and planner decorator attachment for builder systems | U2 | Live view works for a produced builder system |
| T5.4 | Optional dev-mode bytecode transform discovery | T0.6, T5.2 | Non-bean builder systems listed when enabled |
| T5.5 | Deduplication by call site and structure; merge for mixed systems | T5.2 | A per-request builder appears once |
| T5.6 | Run form for `UntypedAgent` from required input keys | T5.2 | Untyped sample runs from the UI |

### Phase 6. Hardening and release

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T6.1 | Config options: enabled, history size, truncation, builder discovery | Phases 2 to 5 | Documented and tested |
| T6.2 | Hot reload: registry reset, open runs closed cleanly | T2.2 | No leaked threads after 10 reloads |
| T6.3 | Performance: listener overhead and a 50-node system | T2.8 | Overhead and render time measured and recorded |
| T6.4 | Documentation page with screenshots | All features | Page merged |
| T6.5 | Compatibility check against `langchain4j-agentic` snapshots in CI | T4.6 | Nightly job green |

## Upstream work

Five changes outside the extension would remove its two riskiest workarounds (bytecode transform and listener injection); U2 and U5 matter most.

| ID | Where | Proposal | Unblocks |
| --- | --- | --- | --- |
| U1 | `quarkus-langchain4j` | Agree on hosting the Dev UI inside the agentic extension's `deployment` and `runtime-dev` modules | T0.1 |
| U2 | `langchain4j-agentic` | A `ServiceLoader`-based observer called whenever a root agentic system is built, able to add listeners and wrap planners | T5.3, and removes T5.4 |
| U3 | `langchain4j-agentic` | Planner decision callbacks on `AgentListener`: the `Action` chosen and, where relevant, the candidates considered | T4.5, simplifies T3.2 |
| U4 | `langchain4j-agentic` | A standard pending-response abstraction for `@HumanInTheLoop`, so tools other than user code can answer it | T2.11 |
| U5 | `quarkus-langchain4j-agentic` | Apply `AgentListener` CDI beans (or a build item) to every agent the extension creates from annotations | T2.4, if the T0.4 spike finds no existing hook |

Each proposal should come with a small PR and a test, since both projects treat the agentic module as experimental and will want minimal API surface.

## Testing, risks and open questions

Tests run on a scripted fake `ChatModel`, so every pattern is deterministic in CI and no API key is needed.

**Testing strategy**

- Unit: topology building per pattern kind, schema generation, event correlation, each pattern adapter.
- Deployment tests (`QuarkusUnitTest`): build-time scan on fixture interfaces, with and without the patterns module.
- JSON-RPC tests: start a run, subscribe, assert the event sequence for sequence, loop, parallel, conditional, supervisor and each planner.
- UI tests with Playwright (`quarkus-playwright`): list, graph render, run form, live highlighting, replay.
- Hot reload test: change an agent interface in dev mode and check the registry updates.

**Risks**

| Risk | Impact | Mitigation |
| --- | --- | --- |
| No hook to attach listeners to Quarkus-built agents | Live view blocked for declarative | T0.4 spike first; U5 upstream; fallback is a dev-mode bytecode transform |
| Builder systems not reachable as beans | Incomplete list | CDI by default, opt-in transform, U2 long term |
| `langchain4j-agentic` and patterns APIs change (both experimental) | Breakage on upgrades | Adapters isolated per pattern; snapshot CI (T6.5) |
| Lambdas hide guard logic in builder systems | Less informative graph | Show "lambda" plus runtime outcome in the live view |
| Runs call real models and real tools | Cost and side effects | Tool confirmation (F3.6); recommend a fake or local model in dev |
| Parallel and async agents interleave events | Wrong node highlighted | Correlate by scope, agent id and invocation id (T2.6) |
| Large payloads in scope or outputs | Slow UI, memory | Truncation and bounded history (F6.3) |

**Open questions**

- Is there a dedicated Quarkus extension for the patterns module, or do apps add `langchain4j-agentic-patterns` directly? This decides how T4.1 detects it.
- Does `quarkus-langchain4j-agentic` already expose a way to customise the builders it uses for annotated agents? (T0.4)
- Can a listener or planner wrapper be added after `.build()`, or only before? (T0.7, T5.3)
- Should the Dev UI reuse `AgentMonitor` and `MonitoredExecution` directly as its trace model, or keep its own events?
- How should streaming agents returning `TokenStream` be shown: token-by-token or final output only?
- Should the graph component be shared with Quarkus Flow (T0.9)?
