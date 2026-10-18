---
id: TASK-71.13
title: 'Annotation scanner: divergences from langchain4j-agentic resolution'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 19:03'
updated_date: '2026-10-17 20:57'
labels:
  - code-review
  - deployment
milestone: m-6
dependencies: []
parent_task_id: TASK-71
priority: low
type: bug
ordinal: 119000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
AgenticAnnotationScanner reads only methods a class declares; langchain4j uses Class.getMethods() so agent methods inherited from a parent interface are missed (node says No agent method found, the parent becomes a stray root). With several agentic methods the scanner picks the first by name; langchain4j has a fixed annotation order. scopeKey ignores @K(Key.class) (TypedKey name), and treats @LoopCounter and InvocationParameters parameters as scope inputs. @RegistryAgent sub-agents are not recognized. Overloaded agent methods give duplicate Class#method ids (registry keeps the last, card count says 2). TopologyBuilder draws conditional sub-agents without @ActivationCondition that langchain4j drops, which also shifts ~n counters. A loop used as a conditional branch loses its activation guard (TopologyBuilder and BuilderTopology). recordSystems tags systems MIXED even with builder-discovery=none; MixedSystems misses Instance<X> injection points.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A sub-agent interface inheriting its agent method from a parent interface is resolved like langchain4j does, without a stray root
- [x] #2 Input keys from @K resolve to the typed key name; @LoopCounter and InvocationParameters parameters are not scope inputs or form fields
- [x] #3 @RegistryAgent sub-agents are shown with their kind
- [x] #4 Overloaded agent methods get distinct ids
- [x] #5 Conditional sub-agents without activation condition are not drawn, and a loop branch shows both its activation and exit conditions
- [x] #6 With builder-discovery=none no system is tagged MIXED
- [x] #7 Deployment tests cover each case
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Scanner: descriptors for indexed subtypes inheriting an agent method without declaring one (AgentDescriptor.inheritedBy, inheritedFrom); scope keys like langchain4j (@P name, @V, @K typed key simple name; no @LoopCounter, InvocationParameters, @UserMessage, scope, memory id); @RegistryAgent as NodeKind.REGISTRY_AGENT; overloaded agent methods named name(param types).
2. RootSystemDetector: a class's agent method chosen in langchain4j's annotation order; supertypes inherited from are referenced (no stray root); conditional sub-agents without activation condition left out of the tree (and of the ~n counting).
3. Guard.activation (+ @RecordableConstructor): a loop that is a conditional branch keeps both conditions (TopologyBuilder, BuilderTopology, graph edge label, node details).
4. CdiRunInvoker: rootMethod resolves name(param types); form keys and skipped parameters match the scanner.
5. MIXED only with builder discovery on (done with TASK-71.7).
6. ScannerParityTest; BuilderDiscoveryDisabledDevModeTest with the DelegatingWorkflow fixture.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ScannerParityTest (7 tests) covers inheritance (no BaseReviewer root), method priority (sequence over @Agent), keys (Story, tone), registry agent, overloads (two systems, each invoking its own method), the dropped conditional branch and the loop branch guard (goodEnough, max 3, activation rough). BuilderDiscoveryDisabledDevModeTest now asserts the delegating workflow stays DECLARATIVE with builder-discovery=none. Found while running: a record with two constructors needs @RecordableConstructor for the build-time recorder (TopologyRecordingTest). Both CI configurations: BUILD SUCCESS (deployment 145/149, integration 106/125). Not covered here: the overridden TypedKey.name() stays runtime-only (same as the existing outputKey rule).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The scan now reads agents as langchain4j-agentic builds them: inherited agent methods resolve (no stray base root), a class's agent method follows langchain4j's annotation order, scope keys follow @P/@V/@K and skip what langchain4j provides, @RegistryAgent has its own kind, overloaded agent methods get distinct ids and run their own method, conditional branches without condition are left out, a loop branch shows both conditions, and builder-discovery=none tags nothing MIXED. Verified with ScannerParityTest, the extended disabled-discovery dev-mode test and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
