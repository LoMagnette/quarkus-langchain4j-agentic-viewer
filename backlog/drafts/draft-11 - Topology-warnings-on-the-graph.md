---
id: DRAFT-11
title: Topology warnings on the graph
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
labels:
  - idea
  - dev-ui
  - deployment
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Catch wiring mistakes before any model call, at build time from the scanned topology: an agent reads a key nothing writes and that is no system input, an output key nobody reads, a conditional branch without condition (dropped by langchain4j), a loop without exit condition and a high max iterations, a human-in-the-loop step fed only by the root (rejected by quarkus-langchain4j-agentic). Shown as badges on nodes and a list on the system page; builder systems checked at runtime the same way.
<!-- SECTION:DESCRIPTION:END -->
