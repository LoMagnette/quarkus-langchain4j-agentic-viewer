---
id: TASK-74
title: Topology warnings on the graph
status: To Do
assignee: []
created_date: '2026-10-17 21:49'
updated_date: '2026-10-18 06:06'
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
- [ ] #1 A conditional sub-agent without activation condition and a loop without exit condition above a configurable maximum are reported as warnings on their nodes
- [ ] #2 Output keys nobody reads and keys read but never written in a system are reported as hints, taking @Output and condition method parameters into account and silenced under supervisors, planners and parallel mappers
- [ ] #3 The system page lists the warnings and hints; a node badge opens them
- [ ] #4 Declarative systems are checked at build time and builder systems at runtime by the same rules
- [ ] #5 Unit tests cover each warning and the main false-positive cases (keys written in code, typed keys, mapper items)
<!-- AC:END -->
