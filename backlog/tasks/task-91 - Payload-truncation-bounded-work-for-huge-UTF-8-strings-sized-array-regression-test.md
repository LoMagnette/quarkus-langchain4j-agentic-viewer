---
id: TASK-91
title: >-
  Payload truncation: bounded work for huge UTF-8 strings, sized-array
  regression test
status: Done
assignee: []
created_date: '2026-10-20 16:33'
updated_date: '2026-10-20 16:51'
labels: []
dependencies: []
priority: low
type: bug
ordinal: 147000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review follow-up of TASK-87: `writeUTF8String` decodes only what can be kept but still scans every byte to count the chars. The review also reported that `writeStartArray(int size)` opens two arrays; the code calls start once, a regression test with a custom serializer using the sized entry points settles it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A UTF-8 string far longer than the limit is neither decoded nor scanned beyond 4x the budget; its marker tells its size in bytes
- [x] #2 A shorter UTF-8 string still tells its exact length in chars
- [x] #3 A custom serializer using writeStartArray(int) and writeStartArray(Object, int) produces exactly its arrays
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Beyond 4x budget bytes (more chars than the budget whatever they are): decode that prefix only, marker "N bytes of UTF-8"; otherwise count chars as now. writeText takes the size text of the marker. 2. Tests: both UTF-8 cases; a Triple serializer writing [a,[b,c]] through the sized entry points.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
UTF-8 beyond 4x budget bytes: only that prefix decoded, not scanned further, marker '… [truncated, N bytes of UTF-8]'; within it the chars are counted as before. writeText/cut take the size text of the marker. The reported double start in writeStartArray(int) is not in the code (one start call); regression test sizedArraysOpenOneArrayEach (custom serializer, writeStartArray(int) and writeStartArray(Object, int)) gives [1,[2,3]] untruncated. Validation: mvn -o clean install 201/107/6 (1 skipped); mvn -o clean install -Ppatterns 210 (1 skipped)/145/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Huge UTF-8 strings are no longer scanned past what could be kept (the marker then tells their size in bytes); a regression test shows the sized array entry points open exactly one array each. Verified with PayloadTruncatorTest (18) and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
