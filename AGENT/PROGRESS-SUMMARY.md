# Quarkus LangChain4j Agentic Viewer - Progress Summary

## Project Overview

A **Quarkus extension** that scans LangChain4j agentic code patterns and generates an interactive **Mermaid.js diagram** in the Quarkus Dev UI, helping developers visualize their AI agent architecture.

**Location**: `/Users/lma/sandbox/lab-quarkus/quarkus-langchain4j-agentic-viewer`

---

## What We Built

### Extension Structure
```
quarkus-langchain4j-agentic-viewer/
├── deployment/
│   └── src/main/
│       ├── java/.../deployment/
│       │   ├── AgenticViewerProcessor.java      # Main build processor
│       │   ├── scanner/
│       │   │   ├── DeclarativeAgentScanner.java # Jandex annotation scanning
│       │   │   └── ImperativeAgentScanner.java  # ASM bytecode scanning
│       │   └── model/
│       │       └── AgenticGraphBuildItem.java
│       └── resources/dev-ui/
│           └── qwc-agentic-viewer.js            # Lit + Mermaid diagram
├── runtime/
│   └── src/main/java/.../runtime/
│       ├── AgenticViewerRecorder.java
│       └── model/
│           ├── AgenticGraph.java
│           ├── AgentNode.java
│           ├── AgentParameter.java              # NEW
│           ├── ToolNode.java
│           ├── DataFlow.java
│           ├── WorkflowComposition.java
│           └── ImperativePattern.java           # NEW
└── integration-tests/
```

---

## Features Implemented

### 1. Declarative Pattern Detection (via Jandex)
Scans for annotations:
- `@Agent`, `@SequenceAgent`, `@ParallelAgent`, `@ConditionalAgent`, `@LoopAgent`
- `@RegisterAiService`, `@Tool`, `@ToolBox`
- `@SystemMessage`, `@UserMessage`, `@V`, `@MemoryId`
- `@Output`, `@A2AClientAgent`

### 2. Imperative Pattern Detection (via ASM)
Detects calls to:
- `AgenticServices.sequenceBuilder()`, `parallelBuilder()`, `conditionalBuilder()`, `loopBuilder()`
- `AgenticServices.agentBuilder(Class)`

Shows warning with details about which builder types are used and where.

### 3. Mermaid Diagram Visualization
- Different shapes for agent types (rectangle, stadium, hexagon, diamond, circle)
- Color-coded styling (agents, sequences, parallels, tools, AI services)
- Workflow compositions (parent → child connections)
- Data flow connections (via `@V` bindings and `outputKey`)
- Tool connections (dashed lines)

### 4. Dev UI Features
- Agent/Tool count stats
- Layout direction toggle (LR, TD, RL, BT)
- Show/Hide Tools filter
- Show/Hide AI Services filter
- Copy Mermaid Code button
- Debug panel with raw JSON data

---

## Key Fixes Applied (Jan 28, 2026)

/### 1. Duplicate Agent Fix
**Problem**: Same class appeared twice (e.g., `PuppyFormFiller` as both `agent_5` and `aiservice_31`)

**Solution**: In `scanAiServices()`, skip classes that already have an agent registered:
```java
if (classToAgentId.containsKey(fullClassName) || classToAgentId.containsKey(simpleClassName)) {
    LOG.infof("Skipping AI service %s - already has agent registered", simpleClassName);
    continue;
}
```

### 2. Input Parameters Display
**Added**: `AgentParameter` model with `name`, `type`, `isMemoryId` fields

**Scanner**: `extractInputParameters()` method extracts:
- Parameter names from `@V` annotations
- `@MemoryId` detection
- Skips framework params like `AgenticScope`

**Viewer**: Shows `methodName(param1: Type1, param2: Type2)` format

### 3. Imperative Pattern Enhancement
- Captures which builder types are used (SEQUENCE, PARALLEL, etc.)
- Captures agent class references from `agentBuilder(Class)` calls
- Shows detailed warning panel with builder badges
- Creates synthetic nodes for detected imperative patterns (dashed style)

### 4. Earlier Fixes (from previous sessions)
- Fixed `subAgents` parsing (nested `@SubAgent[]` annotations, not `Class[]`)
- Fixed Dev UI folder structure (`dev-ui/` not `dev-ui/subfolder/`)
- Added class name display in labels
- Fixed tool linking via `@ToolBox` and `@RegisterAiService(tools={...})`

---

## Current State

**Working**:
- ✅ Declarative agent detection and visualization
- ✅ Workflow compositions (sequence, parallel, conditional, loop)
- ✅ Tool detection and linking
- ✅ Data flow via `@V` bindings
- ✅ Input parameters display
- ✅ No duplicate nodes
- ✅ Imperative pattern warning with details

**Limitations**:
- ⚠️ Imperative patterns (builder API) cannot show full structure since it's runtime-determined
- ⚠️ Lambdas in conditional builders cannot be analyzed

---

## To Test

```bash
cd /Users/lma/sandbox/lab-quarkus/quarkus-langchain4j-agentic-viewer
mvn clean install -DskipTests
```

Then add to a project with LangChain4j agents and open Dev UI at `http://localhost:8080/q/dev-ui/`

---

## Files Modified (Jan 28, 2026)

1. `runtime/.../model/AgentNode.java` - Added `inputParameters` field
2. `runtime/.../model/AgentParameter.java` - NEW file
3. `runtime/.../model/AgenticGraph.java` - Added `imperativePatterns` field
4. `runtime/.../model/ImperativePattern.java` - NEW file
5. `deployment/.../scanner/DeclarativeAgentScanner.java` - Duplicate fix + input params
6. `deployment/.../scanner/ImperativeAgentScanner.java` - Enhanced pattern detection
7. `deployment/.../AgenticViewerProcessor.java` - Serialization updates
8. `deployment/resources/dev-ui/qwc-agentic-viewer.js` - UI improvements

---

---

## Code Review & Fixes (Feb 2, 2026)

A comprehensive code review was performed identifying **15 issues** across the extension.

### Issues Fixed

#### Critical Fixes

| # | Issue | File | Fix Applied |
|---|-------|------|-------------|
| 1 | Null safety in recorder | `AgenticViewerRecorder.java` | Added null check before setting graph |
| 2 | Tool ID collision | `DeclarativeAgentScanner.java` | Changed to `putIfAbsent()` for simple names |

#### High Severity Fixes

| # | Issue | File | Fix Applied |
|---|-------|------|-------------|
| 3 | Resource leak with UncheckedIOException | `ImperativeAgentScanner.java` | Added `UncheckedIOException` to catch blocks |
| 4 | Missing classToAgentId for A2A | `DeclarativeAgentScanner.java` | Added registration for A2A agents |
| 5 | Missing classToAgentId for Output | `DeclarativeAgentScanner.java` | Added registration for Output methods |
| 6 | Wrong AgentType for A2A | `DeclarativeAgentScanner.java` | Changed from `AGENT` to new `A2A` type |

#### Medium Severity Fixes

| # | Issue | File | Fix Applied |
|---|-------|------|-------------|
| 7 | Debug logging in production | `DeclarativeAgentScanner.java` | Changed all `LOG.infof` to `LOG.debugf` |
| 8 | New A2A agent type | `AgentType.java` | Added `A2A` enum value |
| 9 | Frontend A2A support | `qwc-agentic-viewer.js` | Added shape, styling, and remote URL display |

### Files Modified (Feb 2, 2026)

1. `runtime/.../AgenticViewerRecorder.java` - Added null check
2. `runtime/.../model/AgentType.java` - Added `A2A` type
3. `deployment/.../scanner/ImperativeAgentScanner.java` - UncheckedIOException handling
4. `deployment/.../scanner/DeclarativeAgentScanner.java` - Multiple fixes:
   - Tool/agent ID collision prevention (`putIfAbsent`)
   - Debug logging cleanup
   - A2A/Output agent registration
   - Added alternative annotation support for testing (`io.quarkiverse.langchain4j.agentic.*`)
5. `deployment/resources/dev-ui/qwc-agentic-viewer.js` - A2A visualization support
6. `integration-tests/.../Agent.java` - Added `value()` attribute
7. `integration-tests/.../SequenceAgent.java` - Added `subAgents` attribute
8. `integration-tests/.../DeclarativeWorkflowExample.java` - NEW test case for subAgents linking
9. `integration-tests/.../MixedPatternExample.java` - NEW test case for mixed patterns

---

## Remaining Issues (Not Yet Fixed)

These require more significant refactoring:

### Medium Priority

| # | Issue | Description | Effort |
|---|-------|-------------|--------|
| 1 | Scanner state accumulation | Scanner maps accumulate if `scan()` called twice | Medium |
| 2 | findMethod returns first overload | With overloaded methods, wrong one may be matched | Medium |
| 3 | ASM visitor state tracking | `lastLdcClass` tracking is fragile with intervening instructions | High |

### Low Priority

| # | Issue | Description | Effort |
|---|-------|-------------|--------|
| 4 | Mermaid CDN without SRI | Script loaded without subresource integrity check | Low |
| 5 | Inconsistent null handling | Model records allow nullable fields without documentation | Low |
| 6 | Silent JS failures | Mermaid load failures logged but not shown to user | Low |

### Scanning Gaps (Feature Additions)

| Gap | Description | Effort |
|-----|-------------|--------|
| Superclass tools | `@Tool` methods in superclasses not detected | Medium |
| Interface defaults | Default methods in `@RegisterAiService` interfaces not scanned | Medium |
| Multiple agents per class | Same class registered multiple times causes collision | Low |
| Condition classes | `@ConditionalAgent`/`@LoopAgent` predicates not captured | Medium |

### Mixed Declarative/Imperative Pattern Bridge (Feb 3, 2026)

**Implemented**: `bridgeImperativeToDeclarative()` in `AgenticViewerProcessor` now bridges imperative patterns to declarative agents.

| Scenario | Works? | How |
|----------|--------|-----|
| `@SequenceAgent(subAgents={A.class, B.class})` where A,B have `@Agent` | ✅ Yes | Declarative scanner handles directly |
| `sequenceBuilder().add(A.class)` where A has `@Agent` methods | ✅ Yes | Bridge creates synthetic parent, links to A's agents |
| `sequenceBuilder().add(SameClass.class)` (self-referencing) | ✅ Yes | Bridge creates "Workflow" parent, links to all class agents |
| `sequenceBuilder().add(A.class).add(B.class)` (cross-class) | ✅ Yes | Bridge creates parent, links to agents from both A and B |
| `@SequenceAgent(subAgents={A.class})` where A created via `agentBuilder()` | ❌ No | Imperative agents not registered in graph |

**Key design decisions**:
- `classToAllAgentIds` map: A single class can have multiple `@Agent` methods. The bridge resolves a class reference to ALL its agents, not just one.
- Self-reference detection: When `sequenceBuilder().add(X.class)` is called from within class X itself, a synthetic parent node ("X Workflow") is created to avoid self-referencing compositions.
- Deduplication: Child agent IDs are deduplicated before creating compositions.

**Files involved**:
- `ImperativeAgentScanner` detects `builder.add(Class)` calls via ASM (captures `INVOKEVIRTUAL add()` on builder classes)
- `AgenticViewerProcessor.bridgeImperativeToDeclarative()` resolves class refs to agent IDs and creates synthetic nodes/compositions
- `AgenticGraph.withAdditionalAgentsAndCompositions()` merges synthetic data into the graph

---

### Files Modified (Feb 3, 2026)

1. `deployment/.../AgenticViewerProcessor.java` - Added `bridgeImperativeToDeclarative()` method with:
   - `classToAllAgentIds` multi-value map (class → all agent IDs)
   - Self-referencing pattern detection
   - Synthetic parent node creation
   - Composition generation
2. `deployment/.../scanner/ImperativeAgentScanner.java` - Enhanced to capture `builder.add(Class)` calls; reverted temp logging to debug
3. `runtime/.../model/AgenticGraph.java` - Added `withAdditionalAgentsAndCompositions()` method
4. `integration-tests/.../AgenticServices.java` - NEW mock builder API for testing
5. `integration-tests/.../MixedPatternExample.java` - NEW/REWRITTEN with 3 declarative agents + imperative workflow
6. `integration-tests/.../AgenticViewerDevModeTest.java` - Fixed missing `inputParameters` param; added bridge unit test

## Current State

**Working**:
- ✅ Declarative agent detection and visualization
- ✅ Workflow compositions (sequence, parallel, conditional, loop)
- ✅ Tool detection and linking (with collision prevention)
- ✅ Data flow via `@V` bindings
- ✅ Input parameters display
- ✅ No duplicate nodes
- ✅ Imperative pattern warning with details
- ✅ A2A remote agent support with distinct styling
- ✅ Debug logging configurable via log level
- ✅ Mixed declarative/imperative pattern bridging

**Limitations**:
- ⚠️ Purely imperative agents (not annotated) cannot be linked to graph
- ⚠️ Lambdas in conditional builders cannot be analyzed
- ⚠️ Tools in superclasses not detected
- ⚠️ Overloaded methods may cause incorrect data flow analysis

---

## To Test

```bash
cd /Users/lma/sandbox/lab-quarkus/quarkus-langchain4j-agentic-viewer
mvn clean install -DskipTests

# Run integration tests
mvn test -pl integration-tests

# Enable debug logging (in application.properties)
quarkus.log.category."io.quarkiverse.langchain4j.agentic.viewer".level=DEBUG
```

Then add to a project with LangChain4j agents and open Dev UI at `http://localhost:8080/q/dev-ui/`

---

## Next Steps (suggestions)

1. Fix remaining medium-priority issues (scanner state, findMethod overloads)
2. Add SRI hash for Mermaid CDN script
3. Implement superclass tool scanning
4. Add condition/predicate class capture for conditional/loop agents
5. Consider adding click-to-expand for detailed agent info
6. Consider runtime trace overlay (future enhancement)
7. ~~Bridge imperative patterns to declarative agents~~ ✅ Done (Feb 3, 2026)

---

## Spec-driven rework: Backlog + extension skeleton (Oct 6, 2026)

Work now follows `spec-breakdown.md`, decomposed into Backlog.md tasks (`backlog task list --plain`, milestones m-0 … m-7).
Decisions recorded in Backlog: decision-1 (separate Quarkiverse extension, may move into quarkus-langchain4j later),
decision-2 (declarative first; builder API support deferred to milestone m-5).

### TASK-7 (T0.2) Extension skeleton — done

- **New `runtime-dev` module** (`quarkus-langchain4j-agentic-viewer-dev`): dev-mode-only, declared through
  `conditionalDevDependencies` in the runtime extension descriptor (same pattern as `quarkus-arc` / `quarkus-arc-dev`).
  `AgenticViewerRecorder` and `AgenticViewerJsonRpcService` moved there (package `...viewer.runtime.dev`);
  `runtime` now only holds model records. The `recordGraph` build step is dev-only.
- **Real dependencies**: Quarkus 3.30.8 → 3.33.3.1, `quarkus-langchain4j-bom` 1.14.1 (langchain4j-agentic 1.20.2-beta30).
  Stub annotations removed from integration-tests; samples use `dev.langchain4j.agentic(.declarative)`.
- **Dev UI**: new "Agentic Systems" page (`qwc-agentic-systems.js`) calling JSON-RPC `getAgentCount`; JSON-RPC
  service registered dev-only. The Mermaid "Agentic Diagram" page is kept until TASK-26.
- **Prototype scanners**: `ImperativeAgentScanner` targets the real `AgenticServices` and captures all classes passed to
  varargs `subAgents(...)` (replaces the single `lastLdcClass`, remaining issue #3 above). Both scanners now skip
  langchain4j library classes/archives, which are indexed too.
- **Tests** (deployment): `AgenticViewerJsonRpcDevModeTest` (QuarkusDevModeTest + `quarkus-devui-test-spi`),
  `AgenticViewerOutsideDevModeTest` (no card / JSON-RPC outside dev mode). 9 tests in total.

### Things learned

- quarkus-langchain4j-agentic validates agent wiring at build time: every agent parameter must be an output key,
  a supervised key, or a parameter of a root agent that is **injected** somewhere. Output keys of non-AI agents only
  count when listed in a declarative `subAgents` (builder-only chains of non-AI agents fail the build).
- Sub-agents must be separate types listed in `subAgents`; `@Output` methods are `static`.
- Without a model provider extension, an application `ChatModel` bean satisfies quarkus-langchain4j
  (`PlaceholderChatModel` in integration-tests, to be replaced by the scripted fake in TASK-8.1).
- Dev UI JSON-RPC method names are `<namespace>_<method>` in Quarkus 3.33 (namespace `quarkus-langchain4j-agentic-viewer`).
- `QuarkusUnitTest` build-chain customizer steps run in another class loader: report results via system properties,
  compare classes by name.

### To Test

```bash
mvn clean install                       # 9 tests
cd integration-tests && mvn quarkus:dev # Dev UI: http://localhost:8080/q/dev-ui/extensions
```

### TASK-8.1 / TASK-8.2 (T0.3) Deterministic samples — done (Oct 6, 2026)

- **Fake model** (`integration-tests/.../it/fake`): `ScriptedChatModel`, stateless ordered rules matched on request
  content (text replies, tool calls, supervisor decisions); `ChatModelScript` beans contribute rules;
  `ScriptedChatModelProducer` makes it the app's only `ChatModel`. No API key anywhere.
- **9 declarative samples** (`.../it/samples`): StoryWriter (sequence + @Output), ResearchGatherer (parallel),
  PhraseTranslator (parallel mapper), StoryCreator (loop in sequence, 3 iterations), ExpertRouter (conditional),
  BankSupervisor (supervisor + @ToolBox tools), DraftReviewer (custom planner), TripPlanner (human in the loop),
  ResilientStoryWriter (@ErrorHandler). `DeclarativeSamplesTest` runs each one end to end (22 tests in the module).
- **Upstream bug workaround**: quarkus-langchain4j 1.14.1 only wires an application `ChatModel` bean into AI services
  when a provider extension is present, so integration-tests depends on `quarkus-langchain4j-openai` with
  `quarkus.langchain4j.openai.enable-integration=false` (never called).
- **Script lessons**: format instructions are appended to prompts for non-String return types (match the prompt
  line); tool names are not unique (scope tool-result rules to the agent); order supervisor rules most specific first.
- **Finding**: quarkus-langchain4j-agentic 1.14.1 has its own Dev UI (Agents, Topology, Executions, Testing pages),
  overlapping the spec's F1-F4. Decision pending with the project owner.
- **Decision (decision-3)**: after evaluating the upstream Dev UI (TASK-67, Backlog doc-1), the project owner chose to keep
  the full original plan. Upstream bug report drafts are in Backlog doc-2 (not posted).
- **TASK-8.4 patterns samples** (profile `patterns`, `integration-tests/src/patterns/java`): TripGoalPlanner (GOAP),
  ResearchTeam (P2P, exit condition), IncidentResponder (blackboard, custom conflict resolution). Default build excludes
  the module; switch the profile only with `clean` (stale Quarkus app model otherwise). TASK-8 (T0.3) done.

### TASK-9 (T0.4) listener hook spike — done (Oct 6, 2026)

- CDI `AgentListener` beans are attached by quarkus-langchain4j-agentic to **AI agents only** (no workflow nodes, no scope
  lifecycle, no HITL). A listener registered on each **root** agent via the internal
  `InternalAgent.registerInheritedParentListener` (with `inheritedBySubagents() = true`) sees the whole system.
- Neither path reports supervisor/planner decisions (T0.7) or error-handler retries (T2.10).
- Finding: Backlog doc-3. Executable record: `integration-tests/.../it/spike/ListenerHookSpikeTest` (5 tests, 27 in module).

### TASK-12 (T0.7) planner decision spike — done (Oct 6, 2026)

- No public hook to wrap planners of declarative agents before build. After build, the planner supplier is a private final
  field of `PlannerBasedInvocationHandler`; replacing it by reflection on each root works (Java 25) and a delegating
  decorator records every Action for custom, supervisor and sequence planners.
- Finding: Backlog doc-4. Executable record: `integration-tests/.../it/spike/PlannerWrapSpikeTest` (3 tests, 30 in module).

### TASK-13 (T0.8) graph spike — done (Oct 6, 2026)

- Cytoscape.js 3.34.3 + elkjs 0.12.0 from mvnpm (deployment-only; Dev UI serves mvnpm import maps under `/q/_static`).
  elkjs computes a hierarchical layout used as a Cytoscape preset (no cytoscape-elk on mvnpm); import `elkjs/elk.bundled.js`.
- Spike page "Graph (spike)" (`qwc-agentic-graph-spike.js`): compound nodes for the loop-in-sequence fixture, `highlight()`
  API, light/dark theme via `themeState`. Verified with headless Chromium. To be replaced by T1.6.

### TASK-14 / TASK-15 decisions — done (Oct 6, 2026)

- decision-4: own execution event model, reusing AgentMonitor semantics (ids, parent correlation, loop iteration index,
  token usage); AgentMonitor is a test oracle, not storage (it lacks scope lifecycle, tool start, planner decisions,
  scope state, HITL, run ids, push and truncation).
- decision-5: streaming agents show their final output when the stream completes (streaming marker while running).
  Caution: `StreamingResponse.toString()` blocks; listeners must never stringify outputs inline.

### TASK-17 (T1.1) topology model — done (Oct 6, 2026)

- `runtime/.../runtime/topology`: `AgenticSystem` (with `root` tree, `rootNodeId` checked), `AgentNode`, `Guard` (loop exit
  on the LOOP node, activation condition on each CONDITIONAL branch), `PlannerInfo`, `SourceRef`, `SystemInput`, enums.
  Prototype `runtime/model` records stay until TASK-26.
- Tests: JSON round trip (default ObjectMapper) and build-time recording via `BytecodeRecorderImpl`.

### TASK-18 (T1.2) Jandex scan of agentic annotations — done (Oct 16, 2026)

- `deployment/.../scan/AgenticAnnotationScanner` → `AgenticDescriptorsBuildItem`: one `AgentDescriptor` per agent method
  (@Agent, workflow annotations, supervisor, planner + @PlannerSupplier, human in the loop) with keys, ordered sub-agents,
  loop guard (exit method, maxIterations, testExitAtLoopEnd), branch activation guards, @Output method, optional/async,
  and for AI agents `AiAgentDetails` (prompts incl. fromResource, @ModelName, @ToolBox tools as `ToolDescriptor`,
  runtime-only tool sources: suppliers and MCP clients).
- Resolution rules mirror langchain4j-agentic / quarkus-langchain4j-agentic bytecode (first matching
  @ActivationCondition per branch; an unguarded conditional branch is dropped upstream but kept as a sub-agent here).
- Tests: deployment scanner unit tests and a QuarkusUnitTest build step on fixtures; `it/samples/SampleDescriptorsTest`
  runs the scanner on the compiled declarative samples (integration-tests has the deployment jar in test scope).
- Not covered: @UserMessage on a parameter. `AgentNode` has no fields for prompts/model/tools yet (T1.4/T1.7).

### TASK-19 / TASK-20 (T1.3, T1.4) root systems and declarative topology — done (Oct 16, 2026)

- Decision (project owner): `AgentDescriptor` stays deployment-only; the topology is built at build time and only
  `AgenticSystem` values are recorded (runtime-dev) and passed to the Dev UI (`agenticSystems` build-time data).
- `RootSystemDetector` → `AgenticRootsBuildItem`: roots are agents no other agent lists as a sub-agent; `AgentTree`
  expands a shared sub-agent under every parent, cuts cycles, keeps unresolved classes as leaves.
- `TopologyBuilder` → `AgenticSystemsBuildItem`: ids `Class#method` + `/SubAgentSimpleClass` (`~n` for repeats), stable
  across builds; guards, planner supplier (PlannerInfo extras `supplier`), flags and `AiAgentDetails` (moved to runtime
  topology with `ToolInfo`, new `AgentNode.aiDetails`). `inputs` stay empty until T1.9.
- Known limits: `AgentNode` has one guard, so a loop used as a conditional branch shows its exit guard only; an unguarded
  conditional branch is shown although langchain4j drops it (matters for T2.6 position-based correlation).
- Tests: `TopologyBuilderTest` (every node kind, loop in sequence, id stability), `AgenticSystemsDevModeTest`
  (build-time data + recorded systems through a test route), sample root set in integration-tests.

### TASK-21 / TASK-22 (T1.5, T1.6) system list and topology graph — done (Oct 16, 2026)

- Card: the "Agentic Systems" link shows the number of systems (static label). Page `qwc-agentic-systems.js`: grid of the
  `agenticSystems` build-time data (name, origin, pattern kinds, source), empty state, system view with the graph. Dev UI
  card pages cannot pass parameters, so list and system view share one page.
- `qwc-agentic-graph.js`: compound nodes for workflows; `KIND_STYLES` drives shapes and the legend; sequence arrows;
  parallel / mapper / conditional fan out from a fork and join (guard labels on conditional edges, none for an unguarded
  branch); loops get a dashed back-edge bracket labelled max iterations and exit condition (self-loop for one agent);
  supervisor / planner sub-agents are dashed candidates of a hub. API: `highlight`, `clearHighlights`, `stateOf`,
  `renderedNodes`, `renderedEdges`, events `node-selected`, `graph-rendered`.
- ELK findings: hierarchical layout (INCLUDE_CHILDREN) ignores model order, so each compound is laid out on its own with
  its edges; a considerModelOrder option only on a compound crashes elkjs 0.12; bk.fixedAlignment BALANCED centers forks.
  Compounds take their ELK size (min-width/height, top bias) so titles sit inside and edges stop at the border.
- Verified with headless Chromium (Playwright, scripts in the session scratchpad) on all 9 samples, light and dark, plus a
  synthetic system injected into the build-time data. The T0.8 spike page is removed; the prototype "Agentic Diagram"
  page stays until TASK-26.

### TASK-23 / TASK-24 / TASK-25 (T1.7, T1.8, T1.9) details panel, data-flow view, input schemas — done (Oct 16, 2026)

- `qwc-agentic-node-details.js`: clicking a graph node opens a panel (description, keys, flags, guard, planner; prompts,
  model and tools for AI agents; "none" for empty fields); background click or close clears it; the graph refits.
- Data-flow view (toggle in the graph toolbar, `toDataFlowGraph`): agents linked from writer to readers of each scope
  key along ELK orthogonal routes; system inputs are input nodes; keys fed by a parallel mapper ("x ← each xs") or a
  supervisor (its arguments) are attributed to them. Cytoscape ignores `curve-style` in an element's initial bypass:
  set it through a class.
- `InputSchemaGenerator` (deployment/scan): JSON schema per root scope parameter from Jandex types (primitives, strings,
  enums, records, arrays, collections, maps, Optional, date/time/UUID; unknown -> `{}`), on `AgentDescriptor.inputs` and
  `AgenticSystem.inputs`; `SystemInput.required` is false for Optional or any `@Nullable`. `@MemoryId` parameters are not
  inputs (to handle in the run form, T2.7).
- Phase 1 (milestone m-1) tasks T1.1 to T1.9 are done; TASK-26 (retire the prototype view) remains.

### TASK-26 / TASK-68 prototype retired, remote agents — done (Oct 16, 2026)

- The Mermaid page, prototype scanners/model and the asm dependency are removed; the card has one page, Agentic Systems.
  runtime-dev JSON-RPC exposes `getSystemCount()` until T2.1.
- Remote agents: `@A2AClientAgent` (server URL or `@A2AServerUrlSupplier`, tenant) and `@McpClientAgent` (tool name,
  `@McpClientSupplier`) are scanned as node kinds `A2A_CLIENT_AGENT` / `MCP_CLIENT_AGENT` with `RemoteAgentDetails`;
  ellipse / rhomboid in the graph, "Remote" section in the details panel. Tested on a Jandex index only (the A2A/MCP
  runtime modules are not dependencies) and in the browser through a synthetic system.
- Phase 1 (milestone m-1) is complete.

### Phase 2 backend: T2.1 to T2.6 and TASK-27/34 — done (Oct 16, 2026)

- JSON-RPC (runtime-dev `AgenticViewerJsonRpcService`): `listSystems`, `getTopology`, `getInputSchema`, `startRun`,
  `streamRun`; unknown ids are JSON-RPC errors (`DevUIJsonRPCTest` cannot read errors or nulls: raw websocket in tests).
  `AgenticSystemRegistry` holds the systems by id. The Agentic Systems page loads over JSON-RPC.
- Runs: `RunManager` (own daemon threads, events through one event thread, `stream` replays then follows, slow
  subscribers never block), `CdiRunInvoker` (form JSON -> parameters with Jackson; root bean called inside a fresh
  request context, otherwise request-scoped chat memory leaked between runs).
- Events: sealed `ExecutionEvent` records (decision-4/5), `DevUiAgentListener` attached to every root at dev-mode startup
  through `InternalAgent` (`RootListeners`); node ids from the parent invocation + topology (a repeated sub-agent's copies
  share an agentId), loop iterations, mapper item index from the `<agent>_<index>` name; `ScopeStateChanged` per written
  key; payloads bounded and plain (the Dev UI serializes a `JsonNode` as a bean).
- Tests drive `RunManager` directly in `@QuarkusTest` (runtime-dev beans only exist in dev mode); test-only
  `TwiceExclaimed` system in integration-tests/src/test.
- Next: the run UI (T2.7 run form, T2.8 live highlighting, T2.9 timeline/scope/tool panels).

### Phase 2 run UI: T2.7 to T2.10, T2.12 and TASK-41 — done (Oct 16, 2026)

- Run form generated from the input JSON schema (`qwc-agentic-run-form.js`: widgets by type, JSON editor for the rest,
  client-side validation), confirmation listing the reachable tools, live node highlighting (running/done/failed/
  recovered) with duration and tokens on the nodes, cancel.
- Waterfall timeline (`qwc-agentic-timeline.js`: one row per invocation, loop iteration and mapper item, tool calls and
  handled errors nested under their invocation), Scope tab (`qwc-agentic-scope-state.js`), tool arguments and results.
- Bounded run history (`history-size`, default 20), `listRuns` / `getRun` / `cancelRun`, replay of a past run on the
  graph after a page refresh.
- Errors: `ErrorHandled` event (spec extension) from wrapping `DefaultAgenticScope.errorHandler`; retried / recovered
  / fatal shown on the node and in the timeline.
- `JsonRpcEventSequenceTest`: exact event sequence per pattern kind through `startRun` + `streamRun`; mapper items
  interleave and each writes its own `translation_<index>` scope key.

### T2.11 human in the loop from the Dev UI — done (Oct 16, 2026)

- Decision (user): intercept `@HumanInTheLoop` steps in Dev UI runs rather than wait for upstream U4.
- Finding: a declarative step is `AgentExecutor(MethodAgentInvoker(annotated static method), HumanInTheLoop record)`;
  the invoker calls the method directly (the record's `responseProvider` is unused) and both are records. Upstream now
  has `PendingResponse` + `AgenticScope.completePendingResponse`, but only if user code returns one.
- `HumanInTheLoopSteps` replaces each step's executor in `AbstractServiceBuilder.subagents` (copied by per-scope
  handlers) and in the root `PlannerBasedInvocationHandler.subagents` (final field, set by reflection: fine on JDK 25,
  JEP 500 may warn on later JDKs) with a delegating invoker: in a Dev UI run it notifies the listeners, emits
  `AwaitingHumanInput` (prompt = step description, inputs, expected key, value type) and blocks in
  `RunManager.awaitHumanInput`; `provideHumanInput(runId, value)` answers the oldest waiting step (String as typed,
  other types as JSON; a wrong value is a JSON-RPC error and the step keeps waiting); cancel, run end and shutdown
  release waiting steps. AgentMonitor still matches the timeline.
- UI: dashed "waiting" node state, "waiting for input" on the node and the timeline row, paused run panel with the
  prompt, the step's inputs and an answer box; a waiting run reopened from the history after a refresh can be answered.
- Gotcha: the build rejects a step parameter only given to the root (it must be an agent's output key).

### Real model for the samples (TASK-69) and T3.1 supervisor path — done (Oct 16, 2026)

- `-Dquarkus.profile=dev,ollama` runs the sample app on an OpenAI-compatible endpoint (local Ollama by default,
  `qwen2.5:14b`): all 9 samples succeed from the Dev UI. The extension never picks a model.
- T3.1: a supervisor's (or planner's) called sub-agents turn their dashed candidate edge solid, labelled with the call
  numbers ("1", "2, 4"), live and on replay (`LiveRun.calls`, `graph.setPath`, `takenPath()` for checks).
- The graph keeps the run overlay (node states, node info, path edges) and re-applies it after a redraw: switching
  views or themes no longer drops the live highlighting.

### T3.2 planner decisions — done (Oct 16, 2026)

- `PlannerDecisions` wraps every non-built-in planner (supervisor, `@PlannerSupplier`, GOAP / P2P / blackboard) of each
  root's tree in a delegating planner: each Action (call, done, done with result, suspend; no-ops skipped) becomes a
  `PlannerDecision` of the Dev UI run (planner node and invocation, chosen node ids by position among the planner's
  sub-agents, done flag, extras `action` / `result`). Built-in workflows report none.
- Verified: BankSupervisor exchange, withdraw, credit, done with result; DraftReviewer 2 rounds then done; GOAP, P2P
  and blackboard exact sequences (patterns profile); sample outputs unchanged. `AgentTrees` now shared with the HITL steps.

### T3.3 generic planner live view — done (Oct 16, 2026)

- Planner and supervisor steps come from PlannerDecision events: each chosen agent's candidate edge carries its step
  numbers (agents chosen in one decision share it), the planner node shows "step N" then "done after N steps"; the
  timeline has a "Step N → agents" row per decision under the planner invocation and an unnumbered "Done" /
  "Done with result" row. T3.1's numbering from AgentStarted remains the fallback when no decision arrives.

### T3.4 topology contributor SPI — done (Oct 16, 2026)

- `AgenticTopologyContributor` (runtime, inert): `plannerAgent()` names the `@PlannerAgent` interface, `contribute(node)`
  returns extra nodes and labelled edges (ends: sub-agent name or node id, or a contributed node). Found by Jandex,
  applied in dev mode only; failing contributors and edges with unknown ends are logged and skipped.
- The graph draws a contributed planner with the contribution instead of the generic hub + candidates; during a run
  the step numbers go on the nodes ("steps 1, 3, 5"), leaves grow a line and boxes widen to their title when needed.
- Phase 3 (T3.1 to T3.4) is complete.

### T4.1 patterns module detection — done (Oct 16, 2026)

- No Quarkus extension exists for langchain4j-agentic-patterns: apps add it directly; detection checks its planner
  classes at build time. GOAP / P2P / blackboard adapters (runtime-dev `patterns`, optional dependency) are registered
  only for the planners present; `PatternAdapters` hands them to the listener and decisions get `extras.pattern`.
- Without the module: no adapter bean, nothing loaded or logged (dev-mode test); with it (dev mode -Ppatterns): the
  three pattern samples' decisions are tagged GOAP, P2P, BLACKBOARD, the custom planner none.

### T4.2 planner type of each planner node — done (Oct 16, 2026)

- `PlannerTypeDetector` (deployment) reads the `@PlannerSupplier` bytecode (ASM, build class loader): the first object
  constructed that is a planner (pattern planner by name or subclass, or a `Planner` implementation in the index) gives
  `PlannerInfo.plannerClass` and `patternType`; factories hide it (CUSTOM, no class). The details panel shows the class
  and the pattern. Samples: TripGoalPlanner GOAP, ResearchTeam P2P, IncidentResponder BLACKBOARD, DraftReviewer CUSTOM.

### T4.3 GOAP adapter — done (Oct 16, 2026)

- Static: a GOAP planner node is drawn as its key dependency graph (inputs on top, goal key highlighted).
- `previewGoalPath(systemId, inputKeys)` and the live path both run the module's `GoalOrientedSearchGraph` (stand-ins
  from the topology for the preview, the real sub-agents and scope keys at the first decision for the run). The run
  form shows "Planned path" as inputs are filled; a run marks the path "planned", then walks it.
- TripGoalPlanner: preview = computed path = walked path (HotelFinder, WeatherForecaster, ActivityPlanner,
  ItineraryBuilder).

### T4.4 P2P adapter — done (Oct 16, 2026)

- Static: a P2P planner node is a bipartite graph of agents and state keys (reads key -> agent, writes agent -> key),
  cycles included, laid out left to right and folded into rows (ELK graph wrapping).
- Live: written keys light up, an agent lights up when it can activate (inputs all present, one written since it last
  ran, as P2PPlanner), the planner shows "invocations n/max" then "stopped: <reason>" (exit condition met, maximum
  invocations reached, stable state); the timeline's Done row gives the reason.
- `PatternAdapter.Insight` now carries named agent lists (GOAP `path`, P2P `ready`) mapped to node ids.

### T4.5 blackboard adapter — done (Oct 16, 2026), without upstream U3

- Static: a central blackboard node (with the goal key) read and written by the agents (key labels on the edges).
- Per step: the candidates that could fire (activators via reflection, plus the picked one), the strategy
  (declaration order / custom (class) / built-in), invocations against the maximum, whether the goal holds; on done
  goal reached or quiescent. Live: the other candidates light up, the picked one runs, the planner shows
  "invocations n/max · goal not reached" then "stopped: goal reached"; timeline steps
  "→ analyzeMetrics of analyzeLogs, analyzeMetrics · custom (IncidentResponder)".

### T4.6 test matrix — done (Oct 16, 2026); Phase 4 complete

- `.github/workflows/build.yml`: matrix without / with `-Ppatterns` (JDK 21, `mvn -B clean install`), not run on
  GitHub yet (no remote); both commands pass locally.
- deployment has a `patterns` profile too (module as test dependency, `src/patterns-test`): pattern tagging at build
  time and a dev-mode test with the three adapters registered; the no-module dev-mode test is skipped there.

### T6.1 config roots and T6.2 hot reload — done (Oct 16, 2026)

- Config: `quarkus.langchain4j.agentic.dev-ui.enabled` (build time; false removes card, JSON-RPC, beans, listener and
  decorators), `history-size`, `max-payload-size` (run time); generated config docs; dev-mode tests for each.
- Hot reload: `RunManager.shutdown` ends open runs CANCELLED ("Application stopped or reloaded"), completes their
  streams, releases waiting human steps, stops the threads; the registry is rebuilt by the restart. Dev-mode test:
  an agent interface change shows in getTopology, 10 reloads with open runs leave no thread (mutation-checked).

### TASK-70 internal langchain4j API catalog — done (Oct 16, 2026)

- `runs/LangChain4jInternals` lists every internal class, field and method the live hooks use (the hooks take the
  names from it); `LangChain4jInternalsTest` fails on an incompatible library, dev mode logs the missing parts at
  startup (WARN) and keeps the application running.

### TASK-63 prod and native artifact checks — JVM done (Oct 16, 2026), native run pending (local GraalVM)

- `ProdArtifactIT` (every build): the packaged sample app only has the runtime jar (records, SPI, config), no
  runtime-dev / deployment class or Dev UI resource. `NativeArtifactIT` checks the native executable the same way,
  skipped without one (command in CLAUDE.md).

### T6.4 documentation — done (Oct 16, 2026)

- `docs` module: Antora page `docs/modules/ROOT/pages/index.adoc` with 11 screenshots and the config reference
  generated from the config roots.

### TASK-62 Playwright UI tests — done (Oct 17, 2026)

- `deployment/src/ui-test` (profile `ui-tests`): Playwright Java in a `QuarkusDevModeTest` (the Dev UI only exists in
  dev mode; quarkus-playwright targets `@QuarkusTest`), fixture app of two AI agents on a slow echo model. Covers the
  system list, graph render and node details, run form validation, live highlighting (each agent running then done),
  replay of an older run, also after a page refresh; fails on any page script error. CI: one step in the
  without-patterns configuration (Chromium installed with its system dependencies).
- Found and fixed a run form bug: after an error, a click on Run could be lost (the field validated itself on blur,
  hid its error and moved the button under the pointer) and a second error on the same field was not flagged. Fields
  now use manual validation and an edit clears the field's error.

### TASK-66 builder and mixed-style samples — done (Oct 17, 2026), Phase 5 started

- `integration-tests/.../it/builder`: `BuilderStoryCreator` (UntypedAgent bean, builder twin of StoryCreator, lambda
  exit condition), `BuilderStoryWriter` (typed interface bean without agentic annotation, declarative sub-agents),
  `ResearchService` (system in a private field, not a bean), `ExpertResource` (rebuilt per REST request, lambda
  conditions with and without description), `BlurbedStory` (declarative workflow whose non-AI step delegates to the
  builder StoryCreator). `BuilderSamplesTest` runs each on the fake model; `MixedPatternExample` removed.
- Quarkus findings: builder sub-agents given as interfaces are built anew without the app's chat model (fails): pass
  the agent beans, unwrapped from their client proxies; a non-AI agent class is resolved from CDI and must be
  `@Unremovable`; untyped `agentBuilder()` agents ignore their prompt under Quarkus (TASK-8.1).

### TASK-10 spike T0.5 (AgentInstance tree at runtime) — done (Oct 17, 2026)

- Finding in Backlog doc-5, prototype `it/spike/AgentTreeSpikeTest`: the public `AgentInstance` API (plus `as()` for
  loops and conditions, and the annotations of `type()` for AI details) gives the whole topology of builder systems,
  except lambda guard texts ("<unknown>"), builder-given tools, supervisor settings and the system's origin. Builder
  roots declare no inputs. Supervisors and planners report SEQUENCE: the planner type decides the kind.

### TASK-11 spike T0.6 (builder discovery) — done (Oct 17, 2026)

- Finding in Backlog doc-6, prototype `it/spike/BuilderDiscoverySpikeTest`. Recommended default: `cdi`, with a
  build-time scan of producer bodies for agentic `build()` calls (typed interface beans tell nothing by type) and lazy
  creation; `transform` opt-in for non-bean and per-request systems until U2.

### TASK-53 T5.2 runtime topology of builder systems — done (Oct 17, 2026)

- `runtime-dev/.../builder/BuilderTopology.system(id, name, sourceRef, root)`: AgenticSystem from a live
  AgentInstance tree. Kind from the planner class first (supervisors and planners report SEQUENCE), human steps from
  the topology or the @HumanInTheLoop annotation, A2A/MCP from annotations; workflow input keys computed from their
  agents when the builder declares none; lambda guards labelled "lambda"; origin BUILDER, MIXED when a sub-workflow is
  declarative. The builder twin of StoryCreator gives the declarative build-time graph (guard labels aside), and so
  does the declarative StoryCreator read at runtime. Planner class names added to LangChain4jInternals.
- Limits: a builder mapper's items key is private (its inputs are its agent's), A2A builder agents not covered (module
  and server needed).

### TASK-52 T5.1 CDI discovery of builder systems — done (Oct 17, 2026)

- `builder-discovery` build config (`cdi` default, `none`). Build time: `BuilderSystemBeans` picks producer beans typed
  as agents or whose producer calls an agentic `build()`, keeps them unremovable, records bean ids. Runtime:
  `BuilderSystems` creates them on the first listing (registry loads lazily; startup attaches listeners to the
  build-time systems only), keeps those without a parent, builds their topology (BuilderTopology). The Dev UI lists
  them (origin builder) with their graph; Run is disabled and `startRun` refused until T5.6.
- Finding: an agent has one parent; Quarkus shares the agent beans with its declarative systems, so builder systems
  must build their own agents (`agentBuilder(Interface.class).chatModel(..)`); samples and tests changed, regression
  test added. Docs: builder systems section and config reference.

### TASK-58 T5.6 runs of builder systems — done (Oct 17, 2026)

- Form: builder inputs get a JSON schema at runtime (`RuntimeInputSchema`, parity-tested against the build-time
  generator); a typed builder interface takes its inputs from its method. The builder StoryCreator twin gets the
  declarative one's form.
- Runs: `CdiRunInvoker` invokes an UntypedAgent with a map of converted values, a typed interface through its method;
  `BuilderSystems` keeps the root agents. Verified in tests (dev-mode fixture, integration samples) and from the Dev UI
  (builder StoryCreator: "A story about a lighthouse (draft 4)"). The graph is not lit during builder runs (T5.3).

### TASK-54 T5.3 live view of builder systems — done (Oct 17, 2026), without upstream U2

- Builder systems found through CDI are already built: the post-build hooks of the declarative roots apply.
  `BuilderSystems` hands each root to `RunObservation.observe` (one Dev UI listener for all roots): agent events on the
  builder node ids, loop iterations, human steps, planner decisions (new sample `BuilderDraftReviewer`, plannerBuilder).
- Builder workflow inputs: in order for sequences and loops, "read and written by no agent" for the others (a planner's
  agents run in any order): the builder DraftReviewer gets the declarative form (topic only).
- Non-bean systems still need U2 (or the transform, T5.4) to be observed.

### Upstream proposals U2-U6 drafted (Oct 17, 2026), not posted

- Backlog doc-7: U2 observer of built systems, U3 planner decisions on AgentListener, U4 pending responses on
  AgentListener (external completion already exists upstream: `AgenticScope.completePendingResponse`), U5 public
  inherited listener registration + CDI listeners at the roots, U6 bug: an agent shared by two systems is re-parented.
  TASK-2/3/4/16 In Progress, waiting for the owner's go to post.

### TASK-56 T5.4 transform discovery — done (Oct 17, 2026), U2 parked

- `builder-discovery=transform` (adds to cdi): dev-mode bytecode transform of the application classes calling an
  agentic `build()`; `BuiltSystems` keeps the latest instance per call site (a per-request system is listed once),
  registered and observed on the next listing. Tests: transform dev-mode test (field and per-call systems, nested and
  CDI builds not duplicated, run), cdi test (not listed), outside-dev-mode test (no transformer, mutation-checked).
  Samples in dev mode: ExpertResource#ask listed once after 3 requests, ResearchService once used (new
  `/builder/research` endpoint), runs with live events.

### TASK-57.1 T5.5a deduplication — done (Oct 17, 2026)

- `BuiltSystems` keys entries by call site and shape (fingerprint of kinds, names, types; at most 10 shapes per call
  site): a system rebuilt per request is one entry, a call site building different shapes gets `~2`, `~3`.
- HotReloadDevModeTest: the old applications get 30 s (was 5 s) to stop their threads; it failed once on a loaded run.

### TASK-57.2 T5.5b mixed systems — done (Oct 17, 2026); T5.5 complete

- Owner's rule: a builder system is MIXED when it uses an agent class that a declarative system also uses (those
  agents take their build-time details from that node); a declarative system is MIXED when one of its agent beans
  injects a builder system (build time, Arc injection points). Samples: the three builder samples and BlurbedStory are
  MIXED, the page shows "mixed system".
- Fix of T5.4: `BuiltSystems` held instances weakly, so a system built per request vanished at the next GC; now held
  strongly (bounded by call sites and shapes), the transform test forces a GC.
