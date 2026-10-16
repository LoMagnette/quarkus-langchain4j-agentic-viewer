---
id: TASK-18.4
title: 'T1.2d: Scan AI agent details: prompts, model, tools, flags'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 12:25'
labels:
  - deployment
  - discovery
milestone: m-1
dependencies:
  - TASK-18.1
documentation:
  - spec-breakdown.md
parent_task_id: TASK-18
priority: high
type: feature
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T1.2 (feeds F2.5 node details). The details panel shows prompts, model name, tools and optional/async flags; these come from annotations on the agent method and class.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Descriptors record @SystemMessage and @UserMessage templates, description and the configured model name when present
- [x] #2 Tools reachable from each AI agent are recorded with their names
- [x] #3 optional and async flags are recorded
- [x] #4 A deployment test checks all these fields for one AI agent fixture
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Upstream semantics (quarkus-langchain4j-agentic 1.14.1 / langchain4j 1.20.2, from bytecode): model name = @ModelName on the @Agent method (method only, like AgenticProcessor.extractModelName); tools = @ToolBox(classes) on the agent method, each @Tool method of those classes (name = @Tool.name or method name, description = @Tool.value joined); @ToolsSupplier / @ToolProviderSupplier static methods of the agent class and @McpToolBox(clients) only resolve at runtime.
2. @SystemMessage (method, else class) and method-level @UserMessage: value joined by delimiter; fromResource read through the context class loader at build time.
3. optional and async from the agent annotation (@Agent has both, @HumanInTheLoop async only, workflows none -> false).
4. AgentDescriptor gains systemMessage, userMessage, modelName, tools (new ToolDescriptor record: name, declaringClass, methodName, description), runtimeToolSources (Class#method of suppliers, mcp:<client>), optional, async.
5. Fixture: one AI agent with all of these (ModelName, ToolBox over a tool class with named and unnamed @Tool, system/user messages incl. a resource prompt, optional, async); unit test every field; build-step summary covers it in a real app.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Sources mirror quarkus-langchain4j-agentic 1.14.1 (AgenticProcessor bytecode): @ModelName only on the @Agent method; @ToolBox on the agent method (upstream rejects @ToolBox together with @ToolsSupplier on the same agent). AgentDescriptor gained optional, async (from @Agent; @HumanInTheLoop has async only; false for workflows) and aiDetails (new AiAgentDetails record, AI_AGENT only): systemMessage (method, else class; fromResource read through the build's context class loader), userMessage (method-level; value lines joined with the delimiter), modelName, tools (new ToolDescriptor: name = @Tool.name or method name, class, method, description; inherited @Tool methods of indexed superclasses included, ordered by method name per class) and runtimeToolSources (Class#method of @ToolsSupplier/@ToolProviderSupplier, mcp:<client> per @McpToolBox client, mcp:* when none listed). Not covered: @UserMessage on a parameter (the prompt is then the argument itself) and tools from an MCP client or supplier, which only resolve at runtime. The topology AgentNode has no fields for these details yet; T1.4/T1.7 decide how they reach the UI. Test-only stand-in io.quarkiverse.langchain4j.mcp.runtime.McpToolBox in deployment test sources (mcp is not a dependency). Validation: mvn install -pl runtime,runtime-dev,deployment (deployment 28 tests: scanner unit 19, build step on a real app with a @ModelName("fast") model bean and a resource prompt) and mvn verify -pl integration-tests (30), all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
AI agent descriptors now carry system and user prompt templates (resources included), the @ModelName model, @ToolBox tools with their names and descriptions, tool sources only known at runtime, and the optional/async flags of every agent. Verified by scanner unit tests on a fully featured Cashier fixture plus supplier/MCP/flag variants, and by the QuarkusUnitTest build step on a real fixture app; integration tests still pass.
<!-- SECTION:FINAL_SUMMARY:END -->
