---
id: TASK-25
title: 'T1.9: Input JSON schema generation from root method parameters'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-06 06:13'
updated_date: '2026-10-16 13:23'
labels:
  - deployment
  - run
milestone: m-1
dependencies:
  - TASK-18
documentation:
  - spec-breakdown.md
priority: high
type: feature
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spec: T1.9 (feeds F3.1 to F3.3). The run form is generated from a JSON schema of the root method parameters and validated against it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A JSON schema is generated for each typed root system from its method parameters and their names
- [x] #2 Schemas cover primitives, strings, enums, records, lists and maps
- [x] #3 Required parameters are marked required
- [x] #4 Unit tests cover each supported type
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. runtime SystemInput gains 'required' (a per-parameter schema cannot say it); update fixtures/tests.
2. deployment/scan/InputSchemaGenerator (IndexView): JSON schema text from a Jandex Type: boolean, integer (int/long/short/byte, wrappers, BigInteger), number (float/double, wrappers, BigDecimal), string (String, CharSequence, char), enums (enum constants), records (properties per component, required components), arrays / Collection / List / Set (items, uniqueItems for sets), Map<String, V> (additionalProperties), Optional<T> (schema of T, not required), date/time/UUID formats; unknown, unindexed or recursive types -> {} (any JSON). Small JSON writer with escaping (no Jackson in deployment).
3. Scanner: AgentDescriptor.inputs = one SystemInput per scope parameter (same name and order as inputKeys; @MemoryId and AgenticScope excluded); required unless Optional or annotated @Nullable (any package).
4. TopologyBuilder copies the root descriptor's inputs into AgenticSystem.inputs.
5. Tests: generator unit tests per type (fixture interface with every supported type, required/optional, nested record, recursive record); scanner/builder checks; sample roots have inputs (integration-tests SampleDescriptorsTest).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented per plan. SystemInput gained required (runtime record; fixtures updated, one optional to cover JSON and recording). InputSchemaGenerator (deployment/scan, small own JSON writer): boolean, integer (byte/short/int/long, wrappers, BigInteger), number (float/double, wrappers, BigDecimal, Number), string (String, CharSequence; char -> length 1), formats (LocalDate date, LocalTime time, LocalDateTime/OffsetDateTime/ZonedDateTime/Instant date-time, Duration duration, UUID uuid, URI/URL uri), enums (constants), records (properties in declaration order, required components), arrays incl. multi-dimensional, Collection/List/Set (uniqueItems), Map (additionalProperties of the value type), Optional<T> (schema of T); classes that are not records, unindexed types, type variables and recursive records -> {} (any JSON). Required unless Optional or annotated with any annotation simply named Nullable (declaration or type use; matched on the simple name after '.' or '$'). Scanner: AgentDescriptor.inputs for every agent method (same names and order as inputKeys); AgenticScope and @MemoryId parameters excluded, so a root with @MemoryId still needs a memory id from the run form (T2.7). TopologyBuilder copies the root inputs into AgenticSystem.inputs (Dev UI build-time data and runtime-dev get them). Tests: InputSchemaGeneratorTest (9 tests, one fixture method with every type), TopologyBuilderTest root inputs, integration-tests SampleDescriptorsTest.rootInputsHaveJsonSchemas (PhraseTranslator List<String>, BankSupervisor String, every root's inputs match its input keys). Validation: deployment 62 tests, integration-tests 36, all pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Each declarative root system now carries a JSON schema per input parameter (primitives, strings, enums, records, arrays, collections, maps, Optional, dates), with required flags, in its AgenticSystem inputs for the run form. Verified by unit tests per supported type and on the sample systems.
<!-- SECTION:FINAL_SUMMARY:END -->
