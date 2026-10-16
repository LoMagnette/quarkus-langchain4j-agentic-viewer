---
id: TASK-68
title: 'Remote agents: @A2AClientAgent and @McpClientAgent in the topology'
status: To Do
assignee: []
created_date: '2026-10-16 13:34'
labels:
  - deployment
  - discovery
  - dev-ui
milestone: m-1
dependencies:
  - TASK-20
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 103000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The prototype scanner (retired by TASK-26) listed @A2AClientAgent methods; the spec scanner (T1.2) reads neither @A2AClientAgent nor @McpClientAgent. A declarative system using such a remote agent as a sub-agent today shows it as an unresolved leaf ("No agent method found"), so the graph hides that part of the system runs on another server or through an MCP client. Both annotations are listed in quarkus-langchain4j-agentic (AgenticLangChain4jDotNames A2A_AGENT, MCP_CLIENT_AGENT, A2A_SERVER_URL_SUPPLIER, MCP_CLIENT_SUPPLIER).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Methods annotated @A2AClientAgent and @McpClientAgent produce agent descriptors with their own node kind, keys and description
- [ ] #2 The A2A server URL (annotation value or @A2AServerUrlSupplier method) and the MCP client details known at build time are recorded and shown in the node details panel
- [ ] #3 The graph draws remote agents with a distinct shape listed in the legend, in the structure and data-flow views
- [ ] #4 Deployment tests cover both annotations, used as sub-agents of a workflow
<!-- AC:END -->
