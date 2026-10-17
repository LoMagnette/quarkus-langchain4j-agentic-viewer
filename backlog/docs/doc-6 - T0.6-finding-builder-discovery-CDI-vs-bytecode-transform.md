---
id: doc-6
title: 'T0.6 finding: builder discovery, CDI vs bytecode transform'
type: other
created_date: '2026-10-17 06:02'
updated_date: '2026-10-17 06:02'
---
# T0.6 finding: builder discovery, CDI vs bytecode transform

Spike TASK-11. Prototype: `integration-tests/.../it/spike/BuilderDiscoverySpikeTest`, run against the builder samples
of TASK-66 (`it/builder`):
- **CDI**: the beans of the container judged by their types, without creating them (declarative agents, which are
  synthetic beans, left out).
- **Transform**: the call sites of a langchain4j-agentic builder's `build()` in the application bytecode, i.e. what a
  dev-mode transform would wrap (found by a static ASM scan of the same classes).

## Coverage

| Sample | CDI, by bean type | CDI + build-time scan of producer bodies | Transform |
|---|---|---|---|
| `BuilderStoryCreator`: produced `UntypedAgent` bean | yes | yes | yes (2 calls: the nested loop and the root) |
| `BuilderStoryWriter`: produced bean of a typed interface | **no**: its types tell nothing before creation | yes: the producer calls `build()` | yes |
| `ResearchService`: system kept in a field, not a bean | no | no | yes, once the service is created |
| `ExpertResource`: system rebuilt per request | no | no | yes, after the first request; one instance per request |
| `BlurbedStory` (declarative, delegating to a builder system) | declarative: known at build time; the delegate is `BuilderStoryCreator` | | |

Observed: the only bean CDI recognises by type is `PRODUCER_METHOD BuilderStoryCreator [AgenticScopeAccess,
UntypedAgent]` (`SupervisorAgent` beans would be too). Build call sites: `BuilderStoryCreator#storyCreator` (x2,
producer), `BuilderStoryWriter$Producer#storyWriter` (producer), `ResearchService#build`, `ExpertResource#ask` (x2).

## What each strategy implies

**CDI** (T5.1)
- Lists candidates at startup without creating anything; the topology needs the instance, so a candidate is created
  when the Dev UI asks for it (the same instance injection would give; a producer's side effects then happen earlier
  than in the application, but only in dev mode and only when the Agentic Systems page is opened).
- Typed interface beans need a build-time hint: producer methods / fields whose body calls an agentic `build()` (ASM,
  same technique as `PlannerTypeDetector`), else they are only found by creating every interface-typed bean.
- Source: the producer method gives a stable id and a source link (`Class#method`).
- Cost: small (a build step with the producer scan, a runtime lookup, T5.2 for the topology). Maintenance: low, public
  API only (CDI, `AgentInstance`), plus builder owner names for the scan.

**Transform** (T5.4)
- Dev-mode `BytecodeTransformerBuildItem` on application classes: after each agentic `build()` call, pass the result
  to a registry with its call site (`Class#method:line`).
- Sees every system the application builds, beans or not, but only once built: a per-request system appears after the
  first request, a field system after its owner is created.
- Must handle: nested builds (keep roots only: drop a registered system that is a sub-agent of another), repeated
  builds (one entry per call site, latest instance, weak references: T5.5a), hot reload (reset), systems built in
  libraries (not transformed).
- Cost: moderate (transform + registry + dedup + tests, about 2-3 days). Maintenance: medium: rewrites application
  bytecode (dev mode only), depends on the builder owner classes and `build()`; another Quarkus transformer of the
  same classes must compose.

**SPI** (T5.5 / TASK-55): the U2 upstream observer would give what the transform gives without rewriting bytecode.

## Recommendation

- Default `quarkus.langchain4j.agentic.dev-ui.builder-discovery=cdi`, **with the build-time producer scan** so typed
  interface beans are found too (otherwise the most common Quarkus style, a produced typed agent, is missed).
  Candidates are created lazily, when the Dev UI lists or opens them.
- `transform` as an opt-in for systems that are not beans (fields, per-request), until U2 exists; then `spi` replaces
  it. Check U2 before starting T5.4.
- Per-request systems are a poor fit for a viewer of static topology anyway (a new instance each time): the transform
  should key them by call site and show the latest.
