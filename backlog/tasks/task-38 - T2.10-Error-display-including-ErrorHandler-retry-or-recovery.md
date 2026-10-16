---
id: TASK-38
title: 'T2.10: Error display including @ErrorHandler retry or recovery'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:14'
updated_date: '2026-10-16 14:57'
labels:
  - dev-ui
  - live-view
milestone: m-2
dependencies:
  - TASK-36
documentation:
  - spec-breakdown.md
priority: medium
type: feature
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T2.10 (F4.8). An error may be fatal or handled by an @ErrorHandler that retries or recovers; the user must see which happened.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A failed run shows the exception and the failing agent, highlighted on the graph
- [x] #2 When an @ErrorHandler retried or recovered, the outcome is shown on the agent and in the timeline
- [x] #3 The failing sample shows agent, exception and outcome
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Finding: @ErrorHandler recoveries are invisible to listeners (T0.4: a missing argument fails before the agent is invoked); langchain4j calls DefaultAgenticScope.handleError(agentName, exception), which applies the scope's errorHandler field, set by every workflow after the scope is created (withErrorHandler; langchain4j's default rethrows).
2. New event ErrorHandled(nodeId of the failing agent, invocationId of the enclosing workflow invocation, agentName, error, outcome = ErrorRecoveryResult type, result): an extension of the spec event table, needed to tell a fatal error from a retry or recovery.
3. runtime-dev runs/ErrorHandlers (internal API isolated, like RootListeners): at each agent start of a Dev UI run the listener makes sure the scope's errorHandler is wrapped by an observer that delegates to the original and emits ErrorHandled; failing node resolved among the children of the enclosing invocation's node by agent name (topology), else anywhere in the topology.
4. UI: LiveRun marks the node recovered (amber, info 'retried after X' / 'recovered after X') or failed ('failed: X'); timeline rows for handled errors under the enclosing invocation; the run panel names the failing agent and its exception for a failed run.
5. Tests: integration test ResilientStoryWriter without topic -> ErrorHandled(TopicWriter node, MissingArgumentException, RETRY) then a successful retry; a fatal error (handler rethrows) -> ErrorHandled THROW + RunFailed; browser check of the graph, timeline and panel.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented per plan. ErrorHandled event (extension of the spec table, documented on the record; ExecutionEventJsonTest updated). runs/ErrorHandlers (internal API isolated): reflection on DefaultAgenticScope.errorHandler (never null: langchain4j's default rethrows); DevUiAgentListener re-wraps it at each agent start of a Dev UI run (workflows set their handler when they start, after scope creation) with one observer per run; the observer reports agent name, exception, outcome (ErrorRecoveryResult type: RETRY, RETURN_RESULT, THROW_EXCEPTION) and result; failing node = child of the enclosing invocation's node with that agent name, else any node with that name. UI: graph state 'recovered' (amber) with info 'retried' / 'recovered' (short: one line on the node), 'failed' red; timeline row '⚠ <agent>: <Exception> retried|recovered with a result|fatal' under the enclosing invocation; run panel 'in <failing agent>: <Exception>' then the message (failing agent = last fatal handled error, else last failed node). Tests: integration-tests ErrorHandlingEventsTest (ResilientStoryWriter without topic -> ErrorHandled on .../TopicWriter, MissingArgumentException, RETRY, then success; test-only FailingFlow whose scripted model throws -> ErrorHandled THROW_EXCEPTION on .../Exploder, AgentFailed, RunFailed with 'boom on x'; a test-only non-AI agent class could not be built by Quarkus, hence the AI agent). Browser (scratchpad errors-ui.mjs): ResilientStoryWriter started with empty inputs (the form requires topic) -> TopicWriter amber 'retried', timeline row '⚠ writeShortStory: MissingArgumentException retried'; BankSupervisor with an unscripted request -> Failed, 'in transact: IllegalStateException', node red, message shown; no page errors. Validation: deployment 98, integration-tests 53.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Errors are now visible: a failed run names the failing agent and its exception and highlights it, and errors handled by an @ErrorHandler are reported (new ErrorHandled event) and shown as retried or recovered on the agent and in the timeline. Verified by integration tests on the retrying sample and a fatal test system, and in headless Chromium.
<!-- SECTION:FINAL_SUMMARY:END -->
