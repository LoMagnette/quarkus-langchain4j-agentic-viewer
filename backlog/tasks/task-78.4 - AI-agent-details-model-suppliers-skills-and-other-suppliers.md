---
id: TASK-78.4
title: 'AI agent details: model suppliers, skills and other suppliers'
status: To Do
assignee: []
created_date: '2026-10-19 07:45'
labels:
  - review
  - deployment
  - runtime
  - dev-ui
dependencies: []
parent_task_id: TASK-78
priority: medium
type: feature
ordinal: 133000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
Review finding 4. `AiAgentDetails` keeps prompt templates, `@ModelName`, `@ToolBox` tools and runtime tool sources, but not other declarations that tell which model and capabilities an AI agent uses: `@ChatModelSupplier` / `@StreamingChatModelSupplier`, `@Skills` (exposed by the quarkus-langchain4j `DetectedAiAgentBuildItem`), `@ChatMemorySupplier` / `@ChatMemoryProviderSupplier`, `@ContentRetrieverSupplier` / `@RetrievalAugmentorSupplier`, `@SystemMessageProviderSupplier` / `@UserMessageProviderSupplier`, `@AgentListenerSupplier` and `@BeforeCall`. The docs say the node details show models and tools, so a model coming from a supplier, or skills, are missing from what the developer sees.

Related: TASK-77 (guardrails) extends the same record and panel.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The node details of an AI agent show its model source: @ModelName, or the chat / streaming model supplier method (Class#method) when present
- [ ] #2 The node details list the skills of an AI agent
- [ ] #3 The other suppliers (chat memory, content retriever / retrieval augmentor, system / user message provider, agent listener) and @BeforeCall are shown as Class#method references in a compact section
- [ ] #4 Builder systems show what the public API and the annotations of the agent type expose; the rest is documented
- [ ] #5 Scanner tests cover each declaration, a sample uses the main ones, topology parity tests include the new fields
- [ ] #6 User documentation describes the new details
<!-- AC:END -->
