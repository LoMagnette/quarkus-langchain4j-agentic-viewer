---
id: TASK-69
title: 'Sample app: run the samples on a real model (local Ollama, OpenAI-compatible)'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-16 16:44'
updated_date: '2026-10-16 16:51'
labels:
  - samples
  - dx
dependencies: []
type: task
ordinal: 104000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The sample app always runs on the scripted fake chat model, so trying the Dev UI shows canned answers and never a real LLM. The user wants to try the demo on their local Ollama through the OpenAI-compatible API. The extension itself never picks a model: a Dev UI run calls the application's agent beans, which use the model the application configures for each agent; only the sample app forces the fake.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A documented switch starts the sample app in dev mode on a real OpenAI-compatible model (default: local Ollama), with base URL and model overridable by environment variables
- [x] #2 Without the switch the samples and tests still run on the scripted fake model with no API key
- [x] #3 Sample runs started from the Dev UI reach the real model and complete
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ScriptedChatModelProducer only without the 'ollama' build profile (@UnlessBuildProfile).
2. application.properties: %ollama profile enabling the OpenAI integration with base URL ${OLLAMA_BASE_URL:http://localhost:11434/v1}, model ${OLLAMA_MODEL:qwen2.5:14b}, dummy API key, long timeout.
3. Start with mvn quarkus:dev -pl integration-tests -Dquarkus.profile=dev,ollama; document in CLAUDE.md; verify from the sandbox against the host's Ollama (host.docker.internal) by running the samples from the Dev UI.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified from the sandbox against the host's Ollama (qwen2.5:14b, OLLAMA_BASE_URL=http://host.docker.internal:11434/v1): all 9 declarative samples run from the Dev UI (scratchpad pw/real.mjs) and succeed: StoryWriter 51 s, TripPlanner 36 s (step answered from the panel), ExpertRouter 16 s (MedicalExpert chosen), BankSupervisor 20 s (real tool calls, Mario 885), ResearchGatherer 11 s, PhraseTranslator 3 s (3 items), StoryCreator 43 s (1 loop iteration), DraftReviewer 34 s, ResilientStoryWriter 6 s; nodes show real token counts. Default build unchanged: deployment 97, integration-tests 65 on the fake model.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The sample app runs on a real model with -Dquarkus.profile=dev,ollama: the scripted fake is not produced (@UnlessBuildProfile) and the OpenAI extension targets an OpenAI-compatible endpoint (OLLAMA_BASE_URL, default local Ollama; OLLAMA_MODEL, default qwen2.5:14b; OPENAI_API_KEY). Documented in CLAUDE.md. Verified by running all 9 samples from the Dev UI on the host's Ollama; the default build still runs on the fake model.
<!-- SECTION:FINAL_SUMMARY:END -->
