---
id: TASK-74
title: Topology warnings on the graph
status: Done
assignee:
  - '@claude'
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 12:21'
labels:
  - idea
  - dev-ui
  - deployment
dependencies: []
priority: medium
type: feature
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Some wiring mistakes only show at run time, after paying for model calls: a conditional branch that can never run, a loop that will spin to its maximum, a key nobody writes in this system. Showing them on the graph catches them before any run.

## Scope
Warnings computed from the topology, shown as badges on nodes and a list on the system page:
- certain: a conditional sub-agent without activation condition (left out by langchain4j: drawn today only if the scanner keeps it), a loop without exit condition and a high maximum of iterations;
- hints (info level): an output key nobody reads in the system (once @Output and condition method parameters are taken into account); a key read in the system but written nowhere in it and not a system input (silenced under supervisors, planners and parallel mappers, whose arguments are dynamic).
Builder systems get the same checks at runtime.
Dropped from the first idea: a human-in-the-loop step fed only by the root (quarkus-langchain4j-agentic already rejects it, the application does not start).

## Feasibility (research 2026-10-18)
- quarkus-langchain4j-agentic already fails the build when no agent of the application provides a key read by an agent, @Output or @ExitCondition parameter (AgenticProcessor.validateAgenticParameterTypes), application-wide: a per-system check still adds value, but less.
- The topology lacks the parameters of @Output, @ExitCondition and @ActivationCondition methods (deployment-only today): needed, otherwise "output key nobody reads" fires on most last steps. Compute the warnings in Java (deployment, from the descriptors; one shared class used by BuilderTopology at runtime), unit-testable; the topology carries a warnings list per node, the UI draws badges.
- Expected false positives: keys written in code (scope.writeState in non-AI agents, error handlers, planners: DraftReviewer, ResilientStoryWriter samples), typed keys with an overridden name(), mapper item keys, the workflow's final output read by the caller.

## Open questions
- Threshold for "high" max iterations; configurable?
- Can a warning be silenced (annotation, config) or are hints enough?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A conditional sub-agent without activation condition and a loop without exit condition above a configurable maximum are reported as warnings on their nodes
- [x] #2 Output keys nobody reads and keys read but never written in a system are reported as hints, taking @Output and condition method parameters into account and silenced under supervisors, planners and parallel mappers
- [x] #3 The system page lists the warnings and hints; a node badge opens them
- [x] #4 Declarative systems are checked at build time and builder systems at runtime by the same rules
- [x] #5 Unit tests cover each warning and the main false-positive cases (keys written in code, typed keys, mapper items)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime topology: TopologyWarning (node, level WARNING/HINT, code, message) and TopologyChecks, the rules shared by build time and runtime; AgenticSystem.warnings (@RecordableConstructor).
2. Rules: branch-without-condition (dropped conditional sub-agents, from the descriptors), loop-without-exit (no exit condition above loop-warning-iterations, build config, default 5), output-never-read and key-never-written hints, silenced under supervisors, planners and parallel mappers; never-read only when the reads of conditions/@Output are known; never-written left out in systems with a non-AI agent without output key (writes in code).
3. Scanner: keys read by @Output, @ExitCondition and @ActivationCondition parameters; TopologyBuilder runs the checks; BuilderTopology runs them at runtime (reads unknown, a loop without exit told from a lambda: null description).
4. UI: badge on node labels (⚠ / ⓘ), the node's warnings in its details, a collapsible list on the system page opening the node.
5. Tests (TopologyChecksTest incl. scan-based false-positive cases and builder systems, browser test), docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decisions: threshold configurable at build time (quarkus.langchain4j.agentic.dev-ui.loop-warning-iterations, default 5, passed to runtime-dev for builder systems); no silencing annotation, hints are labeled as such. Finding: quarkus-langchain4j-agentic rejects at build time keys no agent of the whole application writes (the first fixture failed that way), so the never-written hint is per system by nature. TopologyChecksTest (7): each rule, typed keys, @Output/condition reads, mapper items, a non-AI writer in code, supervisors/planners, builder loops with and without exit. Both CI configurations BUILD SUCCESS (deployment 169/173, integration 106/127), ui-tests 25/25, config reference regenerated.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Each system's wiring is checked before any run: conditional branches without condition and loops without exit condition (above a configurable limit) are warnings, outputs nothing reads and keys nothing in the system writes are hints, silenced where arguments are dynamic; the same rules apply to declarative systems at build time and builder systems at runtime. Shown as node badges, in node details and as a list on the system page. Verified with unit, scan-based and browser tests and both CI configurations.
<!-- SECTION:FINAL_SUMMARY:END -->
