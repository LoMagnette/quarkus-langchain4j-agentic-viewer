---
id: doc-1
title: Upstream agentic Dev UI vs spec
type: specification
created_date: '2026-10-06 09:17'
updated_date: '2026-10-06 09:22'
---
# Upstream agentic Dev UI vs spec (evaluation, 2026-10-06)

> Outcome: the project owner chose to keep the full original plan (decision-3); the recommendation below was not adopted. Bug drafts: doc-2.

Spike TASK-67. Compares the Dev UI shipped by `quarkus-langchain4j-agentic` 1.14.1 with `spec-breakdown.md` F1–F6.

## Method

- Read the upstream sources: `AgenticDevUIProcessor` (deployment), `AgenticJsonRpcService` and `DevAgentMonitorHolder` (runtime), pages `qwc-agents.js`, `qwc-agents-topology.js`, `qwc-agents-executions.js`, `qwc-agents-testing.js`.
- Ran the 9 declarative samples of `integration-tests` in dev mode, invoked each through the upstream `invokeAgent` JSON-RPC (and StoryCreator through the Testing page with headless Chromium), then rendered every Topology and Execution report. Root interfaces were temporarily made `MonitoredAgent` for this (reverted afterwards).

## What upstream provides

| Page | How it works |
| --- | --- |
| Agents | Build-time table of every user agent (`DetectedAiAgentBuildItem`): name, type, root flag, description, output key, sub-agents, configuration annotations. Filterable. |
| Topology | `HtmlReportGenerator.generateTopology(rootAgent)` from langchain4j-agentic, shown in an iframe. One report per root agent. |
| Executions | `HtmlReportGenerator.generateExecution(agentMonitor)`: sessions and runs as a waterfall, refreshed on demand. |
| Testing | Pick an agent and method, one text field per parameter, synchronous `invokeAgent`, result or error shown. |

Topology and Executions only cover root interfaces that **extend `MonitoredAgent`** (opt-in); those are instantiated at startup and registered in `DevAgentMonitorHolder`. `AgentListener` CDI beans are wired into all agents by the extension.

## Feature comparison

| Spec | Upstream | Evidence / gap |
| --- | --- | --- |
| F1.1 declarative discovery | Covered | Agents page lists all 33 sample agents with types |
| F1.2 roots only, nesting | Covered (Agents) / Partial (Topology) | Root flag on Agents page; Topology needs `MonitoredAgent`; roots named by method name only (two `write` systems indistinguishable) |
| F1.3 builder discovery | Missing | Only Quarkus-built `MonitoredAgent` roots registered (deferred in our backlog anyway) |
| F1.4 dedup, F1.5 hot reload | Partial | `DevAgentMonitorHolder.reset()` at runtime init; not checked further |
| F2.1 nested graph | Covered | StoryCreator: loop box inside sequence |
| F2.2 shapes, loop info | Covered | Kind badges; loop shows `max 5`, exit condition description, `test at end` |
| F2.3 guard labels | Covered | Conditional shows `category is medical/technical/legal` |
| F2.4 supervisor/planner candidates | Missing / wrong | BankSupervisor drawn as `SEQUENCE` with a fixed row of sub-agents; planner class shown as text |
| F2.5 node details | Partial | Type, description, return type, planner, keys; no prompts, model or tools |
| F2.6 data flow | Covered | Key-colored data-flow edges, toggleable |
| F3.1–F3.3 typed form, validation | Partial / missing | Text fields only; `List` parameter fails ("argument type mismatch", PhraseTranslator) |
| F3.4 run from UI | Covered (synchronous) | StoryCreator run from the Testing page |
| F3.5 last inputs, F3.6 tools confirmation | Missing | |
| F4.1 live highlighting | Missing | Post-hoc HTML report, manual refresh, no graph highlighting |
| F4.2 iterations | Covered | Loop rows labelled `iter 0/1/2` |
| F4.3 waterfall | Covered | Nested rows, durations, timeline, tokens column |
| F4.4 scope state panel | Partial | Per-invocation inputs/outputs only |
| F4.5 tool calls | Covered | Tool rows under the agent with arguments and result |
| F4.6 supervisor path | Partial | Order visible in the waterfall (exchange, withdraw, credit), not on the graph |
| F4.7 human in the loop | Missing | HITL step absent from Topology and Executions; no way to answer |
| F4.8 errors and recovery | Missing | ResilientStoryWriter retry invisible, only the final success shown |
| F4.9 past runs | Covered | Sessions and runs kept per root |
| F5 patterns | Missing | Planner class name only (not exercised with GOAP/P2P/blackboard samples, which do not exist yet) |
| F6.1 dev only | Covered for pages | JSON-RPC provider registered without `IsDevelopment` (harmless, not checked further) |
| F6.2–F6.4 non-blocking, truncation, prod | Not assessed | |

Summary: upstream covers static structure and post-hoc execution well (F1.1–1.2, F2.1–2.3, F2.6, F4.2–4.3, F4.5, F4.9). It lacks everything live or interactive and everything planner-specific: live highlighting, supervisor/planner views, patterns, HITL, error recovery, typed run form.

## Bugs and limitations (with reproduction)

1. **App `ChatModel` bean without a provider extension**: AI agents fail at creation ("A synthetic injection point was not declared for required type ChatModel"); `@ChatModelSupplier` agents too. Repro: declarative agent + `@ApplicationScoped` ChatModel bean, no provider extension, invoke the agent. Cause: `AiServicesProcessor` declares the injection point only when a provider is selected. (quarkus-langchain4j core)
2. **Testing page JS error** on load: "Cannot read properties of undefined (reading 'undefined')". Repro: open Testing in the sample app.
3. **Testing: non-scalar parameters** fail: `PhraseTranslator.translateAll(List<String>)` returns "IllegalArgumentException: argument type mismatch" for `{"phrases": "[\"hello\"]"}`.
4. **Topology/Executions silently empty** unless root interfaces extend `MonitoredAgent`; the page only says "No root agents detected."
5. **Ambiguous root names**: entries use the method name only (`write` twice: DraftReviewer and ResilientStoryWriter).
6. **Supervisor shown as SEQUENCE** in the topology.
7. **HITL agents missing** from topology and execution reports (TripPlanner shows only `propose` and `write`).
8. **Error-handler retries invisible** in the execution report.
9. Programmatic untyped agents (`AgenticServices.agentBuilder().userMessage(...)`) ignore the template under Quarkus (build-time generated AI services).

## Recommendation

**Complement, do not duplicate** (mix), and push fixes upstream:

- Do not rebuild what upstream already does well (agent list, static topology, post-hoc waterfall). Consume upstream data instead of re-scanning: `DetectedAiAgentBuildItem` (build time), `AgentMonitor` / `HtmlReportGenerator`, CDI `AgentListener` beans (runtime).
- Focus this extension on what is missing: live execution view with graph highlighting (F4.1), supervisor and planner views (F2.4, F4.6, Phase 3), patterns (F5), HITL (F4.7), error recovery (F4.8), typed run form with validation and tool confirmation (F3).
- Report bugs 1–8 upstream with the samples as reproductions; small fixes (2, 3, 5, 6, 7) are good first contributions and keep the door open to moving features upstream later (decision-1).

Alternative: contribute everything upstream (one Dev UI, no duplication, but dependent on upstream review and release cadence; revisits decision-1).

## Impact on the backlog (if the recommendation is accepted)

- T0.4 (TASK-9) is largely answered: CDI `AgentListener` beans are wired into all Quarkus-built agents. U5 (TASK-16) already exists upstream.
- T1.2/T1.3 (TASK-18, TASK-19): consume `DetectedAiAgentBuildItem` instead of a full Jandex scan; keep only what upstream lacks.
- T1.5/T1.7/T1.8 and T2.9 (list, details, data-flow toggle, waterfall): drop or reduce to links to the upstream pages.
- T1.6 graph component: still needed for live highlighting (an HTML report in an iframe cannot be animated), but scoped to the live view.
- T2.x live view, Phase 3, Phase 4, T2.10, T2.11: unchanged, now the core of the extension.
- New tasks: upstream issues/PRs for bugs 1–8.
