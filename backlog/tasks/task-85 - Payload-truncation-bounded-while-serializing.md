---
id: TASK-85
title: Payload truncation bounded while serializing
status: Done
assignee: []
created_date: '2026-10-20 15:39'
updated_date: '2026-10-20 16:00'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 141000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (medium): `PayloadTruncator.truncate` builds the whole Jackson tree of a value then serializes all of it (`tree.toString()`) before cutting it, so a huge model response or scope value costs memory and CPU proportional to its full size, possibly twice, on the single event thread that delivers every run's events, despite `max-payload-size`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A value is serialized under the budget: strings are cut and containers keep their first entries while serializing, the full tree or full JSON text of a large value is never built
- [x] #2 The work on a value much larger than the limit stops early (it does not walk the whole value)
- [x] #3 Existing truncation behaviour holds: markers, counts of the cut entries, limit respected, streams, cycles and failing getters become markers
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Serialize through a budgeted JsonGeneratorDelegate writing into a TokenBuffer: it keeps the running JSON length, cuts strings to the room left, skips (and counts) the entries of a container once the budget is spent, writes the count marker when the container ends, reserves marker room per open container; past a work cap of skipped tokens it aborts, the open containers then end with a marker without a count. 2. Nesting deeper than 1000 levels = not serializable (cycles). 3. Read the bounded TokenBuffer as the tree. 4. Tests: existing PayloadTruncatorTest, plus a huge list and a huge nested value (bounded work, counter of serialized items), primitive arrays, JsonNode input.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PayloadTruncator now serializes through a budgeted JsonGeneratorDelegate (Bounded) into a TokenBuffer, then reads that bounded buffer as the tree. Strings cut to the room left (char[] copied only up to the budget), container entries skipped and counted once the budget is spent, marker room reserved per open container and taken by the marker on close, every write method routed (primitive arrays, field ids, raw values as text, binary/streams/embedded objects as markers). Past 10000 skipped tokens the serialization stops and open containers end with '… [more items]' / '[more fields]' without a count. Nesting deeper than 1000 = not serializable (cycles). Kept text is 68 chars instead of 67 in nestedObjectsAreShrunkInside (test updated). New tests: a 5M-item list reads < 20000 items, a huge nested value, primitive arrays and JsonNode input, char[] and null strings. Validation: mvn -o clean install 191/106/6 (1 skipped); mvn -o clean install -Ppatterns 200 (1 skipped)/144/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Payload values are cut while Jackson serializes them, through a budgeted generator, instead of after building and printing their full tree; serialization of a value far over the limit stops early. Same markers and limits as before. Verified with PayloadTruncatorTest (15, including bounded-work tests) and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
