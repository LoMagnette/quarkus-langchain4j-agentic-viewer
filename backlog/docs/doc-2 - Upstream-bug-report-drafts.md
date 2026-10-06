---
id: doc-2
title: Upstream bug report drafts
type: other
created_date: '2026-10-06 09:21'
updated_date: '2026-10-06 09:21'
---
# Upstream bug report drafts (2026-10-06)

Drafts only, nothing has been posted. Found while building the samples (TASK-8.1, TASK-8.2) and evaluating the upstream Dev UI (TASK-67, doc-1).
Versions: quarkus-langchain4j 1.14.1, langchain4j-agentic 1.20.2-beta30, Quarkus 3.33.3.1, Java 25.
The `integration-tests` samples of this repository reproduce every issue.

---

## quarkiverse/quarkus-langchain4j

### 1. Application `ChatModel` bean is not injected into AI services when no provider extension is present

**Description.** An application with a CDI `ChatModel` bean and no provider extension builds fine (the build-time check accepts the user bean), but every AI service / AI agent fails when it is created.

**Steps to reproduce.**
1. Depend on `quarkus-langchain4j-agentic` only (no `quarkus-langchain4j-openai`, ollama, ...).
2. Add `@ApplicationScoped class MyModel implements ChatModel { public ChatResponse doChat(ChatRequest r) { ... } }`.
3. Declare an agent `interface Writer { @Agent(outputKey = "text") @UserMessage("Write about {{topic}}") String write(@V("topic") String topic); }`, inject it and call it.

**Expected.** The agent uses `MyModel`.

**Actual.** `CreationException: ... A synthetic injection point was not declared for required type [interface dev.langchain4j.model.chat.ChatModel and qualifiers: [@Default]]`. Agents using `@ChatModelSupplier` fail the same way.

**Cause (analysis).** `AiServicesProcessor` adds the `ChatModel` injection point to the `QuarkusAiServiceContext` synthetic bean only when `selectedChatModelProvider` is not empty, while `AiServicesRecorder` always calls `getInjectedReference(ChatModel.class)` for the default model name. `BeansProcessor.selectProvider` returns `null` (no selected provider) when only a user bean exists.

**Workaround.** Add a provider extension and disable it (`quarkus.langchain4j.openai.enable-integration=false`); the user bean then takes precedence.

### 2. Agentic Dev UI "Testing" page throws a JavaScript error on load

**Steps.** Open Dev UI > LangChain4j Agentic > Testing in an app with declarative agents.
**Actual.** Browser console: `Cannot read properties of undefined (reading 'undefined')`. The page still works after selecting an agent.
**Expected.** No error.

### 3. Agentic Dev UI "Testing" cannot invoke agents with non-scalar parameters

**Steps.** Agent `@ParallelMapperAgent(subAgent = Translator.class, itemsProvider = "phrases") List<String> translateAll(@V("phrases") List<String> phrases)`; in Testing enter `["hello","thank you"]` and invoke.
**Actual.** `IllegalArgumentException: argument type mismatch` (`AgenticJsonRpcService.resolveArguments` passes non-scalar types as String).
**Expected.** JSON input deserialized to the parameter type (lists, maps, records, enums), or the field flagged as unsupported.

### 4. Agentic Dev UI "Topology" and "Executions" are silently empty unless root agents extend `MonitoredAgent`

**Steps.** Declarative root agent not extending `MonitoredAgent`; open Topology.
**Actual.** "No root agents detected." although the Agents page lists the root.
**Expected.** Either all roots are monitored in dev mode, or the page explains that the root interface must extend `MonitoredAgent`.

### 5. Agentic Dev UI root agents are identified by method name only

**Steps.** Two root agents whose methods are both named `write` (e.g. `DraftReviewer.write`, `ResilientStoryWriter.write`), both `MonitoredAgent`.
**Actual.** Topology/Executions selectors show `write` twice.
**Expected.** Show the interface name (and method), e.g. `DraftReviewer.write`.

### 6. Programmatic untyped agents ignore `userMessage(...)` under Quarkus

**Steps.** In a `@QuarkusTest`: `AgenticServices.agentBuilder().name("convert").userMessage("Convert {{amount}} EUR to USD").inputKey(Integer.class, "amount").chatModel(model).build().invoke(Map.of("amount", 100))`.
**Actual.** The model receives the user message `{amount=100}`.
**Expected.** `Convert 100 EUR to USD`, as with plain langchain4j. (Probably because AI services are generated at build time; at least worth documenting.)

---

## langchain4j/langchain4j (langchain4j-agentic)

### 7. Supervisor agents are reported with a SEQUENCE topology

**Description.** `SupervisorPlanner` does not override `Planner.topology()`, so it inherits `AgenticSystemTopology.SEQUENCE`; `HtmlReportGenerator.generateTopology` then draws a supervisor as a fixed sequence of its sub-agents. `AgenticSystemTopology.STAR` looks intended for this case.
**Steps.** `@SupervisorAgent(subAgents = { ExchangeAgent.class, WithdrawAgent.class, CreditAgent.class })`, generate the topology report.
**Expected.** A star / candidates topology. **Actual.** "SEQUENCE" badge and a fixed row of sub-agents.

### 8. Human-in-the-loop agents are missing from topology and execution reports

**Steps.** `@SequenceAgent(subAgents = { DestinationProposer.class, TravelerApproval.class, ItineraryWriter.class })` where `TravelerApproval` has a static `@HumanInTheLoop(outputKey = "approval")` method; run it with an `AgentMonitor` and generate both reports.
**Actual.** Only `propose` and `write` appear; the human step is absent although its output is used.
**Expected.** A HUMAN_IN_THE_LOOP node in the topology and an invocation row (with the answer) in the execution report. Root cause not investigated.

### 9. Error-handler retries are not visible in the execution report

**Steps.** `@SequenceAgent(subAgents = TopicWriter.class) String write(@V("topic") String topic)` with an `@ErrorHandler` that writes a default topic and returns `ErrorRecoveryResult.retry()` on `MissingArgumentException`; invoke with `null`.
**Actual.** The execution report shows a single successful `writeShortStory` invocation.
**Expected.** The failed attempt (with the exception) and the retry are visible. Root cause not investigated.
