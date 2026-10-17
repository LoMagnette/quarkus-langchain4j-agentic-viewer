---
id: doc-5
title: 'T0.5 finding: topology of a live AgentInstance tree'
type: other
created_date: '2026-10-17 06:00'
updated_date: '2026-10-17 06:00'
---
# T0.5 finding: topology of a live AgentInstance tree

Spike TASK-10. Prototype: `integration-tests/.../it/spike/AgentTreeSpikeTest` prints the tree of builder and declarative
systems (`target/agent-trees.txt`) through the **public** `dev.langchain4j.agentic.planner.AgentInstance` API only
(langchain4j-agentic 1.20.2-beta30, quarkus-langchain4j-agentic 1.14.1), and checks every point below.

## Getting the tree

- Every agent bean, declarative or produced by a builder, is an `AgentInstance` once unwrapped from its CDI client
  proxy (`ClientProxy.unwrap`). No internal API is needed to walk it: `subagents()` recurses.
- Loop and conditional details come through `agent.as(LoopAgentInstance.class)` /
  `as(ConditionalAgentInstance.class)` (ClassCastException otherwise); the agents are no instances of these interfaces.
- `agentId()` is not stable across systems: a sub-agent's id gets one `$<index>` suffix per nesting level, and builder
  sub-agents passed as beans get extra ones (`score$0$1$0$1` vs `score$0$1`). Node ids must keep being derived from our
  own walk (name or type per level), not from `agentId()`.

## Field by field (our `AgentNode` / `AgenticSystem`)

| Model field | Builder system | Declarative system | Source |
|---|---|---|---|
| kind | yes, with care | yes, with care | `topology()` + `plannerType()` (below) |
| name | yes (`name(..)`, else method name of a typed agent) | yes (method name) | `name()` |
| description | only when the builder sets it (else `null`) | yes | `description()` |
| outputKey | yes; `null` when the workflow uses `output(fn)` | yes | `outputKey()` |
| output type | `Object` for UntypedAgent and workflows | declared type | `outputType()` |
| inputKeys | leaves: yes; builder workflows: **none** (`arguments()` empty) | yes | `arguments()`: name, type, optional, default |
| optional / async | yes | yes | `optional()`, `async()` |
| children | yes, in order | yes, in order | `subagents()` |
| loop guard | max iterations, test at end: yes; exit condition label: `"<unknown>"` for a lambda without description | all, label = `@ExitCondition` description | `LoopAgentInstance` |
| conditional guards | label of `subAgents(description, predicate, ..)`, `"<unknown>"` without description | `@ActivationCondition` description | `ConditionalAgentInstance.conditionalSubagents()` (condition, agents) |
| plannerInfo.plannerClass | yes (`plannerType()`, e.g. `DraftReviewPlanner`) | yes | `plannerType()`; pattern type from the class as at build time |
| aiDetails (prompts, model, tools) | not in AgentInstance; recoverable by reflection on `type()` when the AI agent is a typed interface (always the case under Quarkus, see TASK-66): `@SystemMessage`, `@UserMessage`, `@ModelName`, `@ToolBox` | same (build time already has them) | `type()` annotations |
| tools given to a builder (`.tools(..)`) | not reachable without internal API | n/a | - |
| remote details (A2A, MCP) | not checked (needs a server); `type()` should carry the annotations | build time | - |
| system id / name / sourceRef | none: no class#method; needs the discovery (bean name, producer method, call site) | build time | T5.1 |
| system inputs (run form) | builder roots declare none: union of the leaves' arguments not produced by an earlier agent (as `AgenticSystemTopology` data types do upstream) | build time | T5.6 |

### Kinds

- `topology()`: AI_AGENT, NON_AI_AGENT, HUMAN_IN_THE_LOOP, SEQUENCE, PARALLEL, LOOP, ROUTER, STAR.
- Supervisors (`SupervisorPlanner`) and custom planners report **SEQUENCE**: map `plannerType()` first
  (SupervisorPlanner -> SUPERVISOR, ParallelMapperPlanner -> PARALLEL_MAPPER, SequentialPlanner / ParallelPlanner /
  LoopPlanner / ConditionalPlanner -> their kind, any other planner -> PLANNER), then `topology()`.
- A parallel mapper reports PARALLEL with `ParallelMapperPlanner`.
- Human-in-the-loop: a builder step (`humanInTheLoopBuilder()`) is HUMAN_IN_THE_LOOP (`type()` = `HumanInTheLoop`,
  name `askUser` unless set); a declarative `@HumanInTheLoop` step is a NON_AI_AGENT whose `type()` carries the
  annotation: check the annotation to tell it.

## Not recoverable at runtime

- Lambda guard texts (loop exit conditions and activation conditions without description): shown as "lambda".
- Supervisor configuration (response / context strategy, max invocations) and `output(fn)` functions.
- Tools and models given to a builder agent directly; untyped `agentBuilder()` agents (which ignore their prompt under
  Quarkus anyway, TASK-8.1).
- Where the system was built: needed for T5.5 deduplication (call site) and must come from the discovery strategy.

## Conclusions for Phase 5

- T5.2 can build the topology model from the public API alone (plus annotations of `type()`), so it survives
  langchain4j internals changes better than the run-time hooks do.
- The builder twin of StoryCreator has the same shape (kinds, names, output keys, loop settings) as the declarative one;
  only the exit condition label and the root's inputs / output type differ (asserted by the prototype).
- Builder roots need inputs computed from their leaves for the run form (T5.6).
