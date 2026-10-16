---
id: TASK-39.1
title: 'T2.11a: AwaitingHumanInput event and provideHumanInput JSON-RPC'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 15:33'
labels:
  - runtime-dev
  - json-rpc
milestone: m-2
dependencies: []
documentation:
  - spec-breakdown.md
parent_task_id: TASK-39
priority: medium
type: feature
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Part of T2.11.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An AwaitingHumanInput event with runId, prompt and expected key is emitted when a HITL step waits
- [x] #2 provideHumanInput(runId, value) completes the pending step and returns true, or false if nothing is pending
- [x] #3 A JSON-RPC test completes the HITL sample
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Decision (user, Oct 16 2026): intercept @HumanInTheLoop steps in Dev UI runs instead of waiting for upstream U4.
Finding: declarative HITL steps are AgentExecutor(MethodAgentInvoker(user static method), HumanInTheLoop record); the invoker calls the user method directly (the record's responseProvider is unused), both records are immutable. Upstream's PendingResponse/completePendingResponse would need the user method to return one.
1. HumanInTheLoopSteps (runs, internal API isolated like ErrorHandlers): walks each root agent (AbstractQuarkusAgent.agent -> PlannerBasedInvocationHandler -> service.subagents, the mutable list per-scope handlers copy, plus the handler's own list used by root calls; recursing into nested workflows) and replaces every AgentExecutor whose agent is a HumanInTheLoop with one using a delegating invoker. Installed by RootListeners.attach, with the listener.
2. The delegating invoker: outside Dev UI runs of its listener's manager it delegates unchanged; in a Dev UI run it notifies the listeners (ListenerNotifierUtil, so the node starts/finishes as usual), emits AwaitingHumanInput and blocks on RunManager until provideHumanInput or cancel; the user method is not called.
3. RunManager: pending human inputs per run; provideHumanInput(runId, value) converts the text to the step's return type (String as is, else JSON) and completes the oldest pending step: true, false if none pending, IllegalArgumentException for a value of the wrong type; cancel fails pending steps.
4. AwaitingHumanInput gains invocationId, valueType and inputs (the step's arguments, bounded) so the UI can show what is asked about.
5. JSON-RPC provideHumanInput; tests: TripPlanner completes through startRun + AwaitingHumanInput + provideHumanInput, approval and refusal, wrong type, nothing pending, cancel while waiting, runs outside the Dev UI still call the user method; existing sample tests answer the HITL step.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented as planned. Probes showed the HumanInTheLoop record's responseProvider is never used in the declarative path and that root calls read PlannerBasedInvocationHandler.subagents (immutable, final) while per-scope handlers copy AbstractServiceBuilder.subagents: both are patched. Interceptors chain when several Dev UI listeners are attached (tests): each handles only its own manager's runs. Upstream build validation rejects a HITL parameter only provided by the root (CopiesOrder test system reads an agent output). Tests: HumanInTheLoopRunTest (6: approve, refuse, int answer as JSON + wrong type, cancel while waiting, unknown run, direct calls still call the method), AgenticSystemsDevModeTest (provideHumanInput over the websocket: false when nothing waits, error for an unknown run), TimelineMatchesAgentMonitorTest still matches AgentMonitor with the step intercepted. Full build: deployment 97, integration-tests 65, patterns profile 71, all passing.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Dev UI runs answer @HumanInTheLoop steps: HumanInTheLoopSteps (installed by RootListeners.attach) swaps each step's invoker for one that, in a Dev UI run, notifies the listeners, emits AwaitingHumanInput (node, invocation, prompt, expected key, value type, inputs) and waits in RunManager until provideHumanInput(runId, value) (JSON-RPC; String as typed, other types as JSON; false when nothing waits) or cancel/run end. Outside Dev UI runs the annotated method is called as before. Verified by HumanInTheLoopRunTest, AgenticSystemsDevModeTest, the AgentMonitor oracle test and the full build.
<!-- SECTION:FINAL_SUMMARY:END -->
