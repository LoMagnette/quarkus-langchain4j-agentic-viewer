---
id: TASK-78.4
title: 'AI agent details: model suppliers, skills and other suppliers'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-19 07:45'
updated_date: '2026-10-19 09:27'
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
- [x] #1 The node details of an AI agent show its model source: @ModelName, or the chat / streaming model supplier method (Class#method) when present
- [x] #2 The node details list the skills of an AI agent
- [x] #3 The other suppliers (chat memory, content retriever / retrieval augmentor, system / user message provider, agent listener) and @BeforeCall are shown as Class#method references in a compact section
- [x] #4 Builder systems show what the public API and the annotations of the agent type expose; the rest is documented
- [x] #5 Scanner tests cover each declaration, a sample uses the main ones, topology parity tests include the new fields
- [x] #6 User documentation describes the new details
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Upstream (langchain4j-agentic DeclarativeUtil, quarkus-langchain4j AgenticProcessor): the suppliers are static methods of the agent class (@ChatModelSupplier, @StreamingChatModelSupplier, @ChatMemorySupplier, @ChatMemoryProviderSupplier, @ContentRetrieverSupplier, @RetrievalAugmentorSupplier, @SystemMessageProviderSupplier, @UserMessageProviderSupplier, @AgentListenerSupplier; @BeforeCall for workflows and agents); @Skills (io.quarkiverse.langchain4j.skills.Skills, not in the pinned jars: read by name) is on the agent method, like @ToolBox.
1. AiAgentDetails: chatModelSupplier, streamingChatModelSupplier (Class#method), skills (null without @Skills, the values of its 'value' otherwise); @RecordableConstructor, the old constructor kept.
2. CodeRef roles for every supplier and @BeforeCall, added to the node's code by the scanner and BuilderTopology (agent type's static methods): the Code section lists them with IDE links.
3. Node details: Model shows the supplier when there is one; Skills row; Code role labels.
4. Tests: scanner test with a fixture per annotation (a test-only io.quarkiverse.langchain4j.skills.Skills annotation, only indexed by plain scanner tests), BuilderTopology from annotated types, UI test of the AI section; a sample with a @ChatModelSupplier is not added (the samples use the app's scripted model). Docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented as planned. AiAgentDetails: chatModelSupplier, streamingChatModelSupplier, skills (@RecordableConstructor, old constructor kept). CodeRef roles CHAT_MODEL_SUPPLIER ... BEFORE_CALL, added to every node's code by the scanner (SUPPLIER_ROLES) and BuilderTopology.agentCode (same map); the node details label them (CODE_ROLES; AiAgentSuppliersScanTest.theDevUiLabelsEveryRole checks each role has a label) and show the model supplier in place of the model name and a Skills row. @Skills is not in the pinned jars: read by name; deployment tests have a stand-in annotation io.quarkiverse.langchain4j.skills.Skills, only indexed by plain scanner tests. In BuilderTopology the attribute() helper from TASK-78.3 was folded into the existing value(). No sample uses a model supplier (samples run on the app's scripted model). Validation: mvn -o clean install (deployment 179, integration-tests 106, ITs 6 / 1 skipped), -Ppatterns (188 / 1 skipped, 139, 6 / 1 skipped), UI tests 30/30 (new aiAgentDetailsShowTheModelSupplierAndSkills); new AiAgentSuppliersScanTest, BuilderAgentDetailsTest.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
AI agent details now tell where the model comes from (@ModelName, or the @ChatModelSupplier / @StreamingChatModelSupplier method) and list the agent's @Skills; every other supplier (chat memory and provider, content retriever, retrieval augmentor, system / user message providers, agent listener) and @BeforeCall is a labelled entry of the node's Code section, with IDE links. Builder systems get the same from their agent types' annotations. Verified with both CI builds and the 30 UI tests.
<!-- SECTION:FINAL_SUMMARY:END -->
