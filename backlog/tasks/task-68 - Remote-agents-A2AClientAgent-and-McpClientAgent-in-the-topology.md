---
id: TASK-68
title: 'Remote agents: @A2AClientAgent and @McpClientAgent in the topology'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-16 13:34'
updated_date: '2026-10-16 13:40'
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
- [x] #1 Methods annotated @A2AClientAgent and @McpClientAgent produce agent descriptors with their own node kind, keys and description
- [x] #2 The A2A server URL (annotation value or @A2AServerUrlSupplier method) and the MCP client details known at build time are recorded and shown in the node details panel
- [x] #3 The graph draws remote agents with a distinct shape listed in the legend, in the structure and data-flow views
- [x] #4 Deployment tests cover both annotations, used as sub-agents of a workflow
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Upstream rules (langchain4j-agentic 1.20.2-beta30 AgenticServices bytecode): @A2AClientAgent(a2aServerUrl, tenant, name/value/description, outputKey/typedOutputKey, async) needs exactly one of a2aServerUrl or a static @A2AServerUrlSupplier method of the declaring class; @McpClientAgent(toolName, ...) gets its client from a static @McpClientSupplier method; input keys come from the method parameters like other agents.
2. runtime: NodeKind A2A_CLIENT_AGENT and MCP_CLIENT_AGENT; new RemoteAgentDetails record (a2aServerUrl, serverUrlSupplier, tenant for A2A; toolName, clientSupplier for MCP); AgentNode.remoteDetails.
3. Scanner: both annotations as agent kinds (descriptor fields as for other agents, plus remoteDetails); TopologyBuilder copies them.
4. Dev UI: KIND_STYLES / KIND_LABELS entries (own shape and color, legend in both views), systems page counts them as agents not patterns, details panel 'Remote' section (A2A server or supplier, tenant; MCP tool and client supplier).
5. Tests: scanner and topology unit tests with a test-only sequence using an A2A agent (URL and tenant), an A2A agent with a URL supplier and an MCP agent with a client supplier (no app: the A2A/MCP runtime modules are not dependencies); browser check through a synthetic system injected into the build-time data.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented per plan. runtime: NodeKind A2A_CLIENT_AGENT, MCP_CLIENT_AGENT; RemoteAgentDetails(a2aServerUrl, serverUrlSupplier, tenant, toolName, clientSupplier); AgentNode.remoteDetails. Scanner: both annotations in the kind map (name/value/description, outputKey/typedOutputKey, async read like other agents), remoteDetails from the annotation and the @A2AServerUrlSupplier / @McpClientSupplier method of the declaring class; not workflows (no @Output lookup), no aiDetails. TopologyBuilder copies remoteDetails. Dev UI: A2A agent ellipse sky-blue, MCP client agent rhomboid lime (barrel was indistinguishable from a rectangle), labels in KIND_LABELS so the legend lists them in both views; the systems page counts them as agents, not patterns; details panel 'Remote A2A server' (server URL or 'from Supplier#method', tenant) and 'MCP server' (tool, client supplier). Tests: RemoteAgentsTest (4 tests: A2A with URL and tenant, A2A with URL supplier and async, MCP with client supplier, all three as resolved sub-agents of a sequence in the topology); Jandex index only because the A2A/MCP runtime modules of langchain4j-agentic are not dependencies. Browser (headless Chromium, synthetic system injected into the build-time data, light and dark): shapes ellipse/ellipse/rhomboid, legend 'A2A agent | MCP client agent' in structure and data-flow views, panel fields a2aServer http://localhost:8081 / tenant acme, 'from Reviewer#serverUrl', mcpTool web_search / 'from Search#client', data-flow edges text -> translate -> reviewer -> search, no page errors; the 9 samples still pass the layout checks. Validation: deployment 65 tests, integration-tests 31, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A2A and MCP client agents are scanned as their own node kinds with their server URL (or supplier), tenant, MCP tool and client supplier, drawn with distinct shapes listed in the legend of both views and detailed in the node panel. Verified by deployment tests using both as sub-agents of a workflow and by a headless Chromium check on a synthetic system.
<!-- SECTION:FINAL_SUMMARY:END -->
