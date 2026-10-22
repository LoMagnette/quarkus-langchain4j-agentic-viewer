---
id: TASK-94
title: 'Sample app: REST endpoints running every sample scenario'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-22 14:28'
updated_date: '2026-10-22 14:32'
labels: []
dependencies: []
priority: low
ordinal: 150000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The sample app (integration-tests) only had three REST endpoints (/test/research, /builder/research, /builder/expert); every other sample (declarative, builder, mixed, patterns) could only be run from the Dev UI or from tests. The owner wants to trigger each scenario over HTTP (curl, load tools), e.g. to exercise an application-side call next to the Dev UI. Inputs default to those the scripted fake model answers; runs called this way are application calls, not Dev UI runs, so @HumanInTheLoop steps get their answer from HumanInput (approves by default).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /scenarios lists every scenario endpoint with its query parameters
- [x] #2 Each declarative, builder and mixed sample has a GET endpoint under /scenarios that runs it, with query parameters defaulting to inputs the fake model answers
- [x] #3 With -Ppatterns, each patterns sample has a GET endpoint under /scenarios/patterns, listed by GET /scenarios/patterns
- [x] #4 Tests call every endpoint with its defaults and check the output, in the default build and with -Ppatterns
- [x] #5 CLAUDE.md mentions the endpoints
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ScenarioResource (/scenarios, main sources): one GET per declarative, builder and mixed sample, query params with @DefaultValue taken from the tests inputs; GET /scenarios returns a text index (also lists the existing /builder/research and /builder/expert; /test/research is the same system as /scenarios/research-gatherer).
2. PatternScenarioResource (/scenarios/patterns, src/patterns): same for the seven pattern samples.
3. Plain-text responses (no Jackson in the sample app); list outputs one per line.
4. ScenarioResourceTest (src/test) and PatternScenarioResourceTest (src/patterns-test) with rest-assured, expected outputs as in DeclarativeSamplesTest / BuilderSamplesTest / PatternSamplesTest; every index entry called with its example query.
5. CLAUDE.md: mention the endpoints next to the sample app command.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified in a scratch copy of integration-tests with the committed pom: the working tree pom has an uncommitted quarkus-flow-langchain4j dependency (not part of this task) whose build step fails on CopiesOrder$CopiesQuestion ("No agent method found"), so the module does not build as it stands. mvn clean verify: 112 tests + ITs green (ScenarioResourceTest 5/5); mvn clean verify -Ppatterns: 152 tests + ITs green (PatternScenarioResourceTest 2/2).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added ScenarioResource (GET /scenarios index + one GET per declarative, builder and mixed sample) and PatternScenarioResource (/scenarios/patterns, profile patterns), plain text, query parameters defaulting to inputs the scripted fake model answers. Calls are application calls (not shown in the Dev UI; human steps answered by HumanInput). Verified with ScenarioResourceTest and PatternScenarioResourceTest (expected outputs of the sample tests, every index entry called), full integration suites green with and without -Ppatterns. CLAUDE.md and PROGRESS-SUMMARY updated.
<!-- SECTION:FINAL_SUMMARY:END -->
