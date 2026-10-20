---
id: TASK-87
title: >-
  Payload truncation: root and field values within the budget, UTF-8 strings
  decoded only up to it
status: Done
assignee: []
created_date: '2026-10-20 16:04'
updated_date: '2026-10-20 16:21'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 143000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code review (medium), follow-up of TASK-85: the budgeted writer of `PayloadTruncator` checks the room left only for array entries, so a root value or an object field value that is a huge BigInteger/BigDecimal, encoded number or binary is written whole (`truncate(BigInteger.TEN.pow(10000))` exceeds `max-payload-size`). `writeUTF8String` / `writeRawUTF8String` (custom serializers) still decode the whole input before cutting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A scalar too large for the room left is replaced by a cut string marker at the root, in an object field and in an array, so the JSON never exceeds the limit
- [x] #2 A field name already written always gets a valid value
- [x] #3 UTF-8 strings are decoded only up to what can be kept, the marker still tells the full length in characters
- [x] #4 Huge numbers are not turned into their full decimal text to measure them
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. One path for every scalar: its JSON text (or a bounded estimate) and size; when it does not fit the room (root, field or array), write its cut text as a string (in an array: skipped and counted when not even a cut one fits). 2. BigInteger/BigDecimal: estimate the digits from bitLength, the full text only when it can fit; beyond, a marker string with the approximate digit count. 3. Binary: base64 when it fits, else a marker. 4. UTF-8: count UTF-16 chars from the bytes, decode only a prefix of at most 4x the budget. 5. Tests: huge BigInteger/BigDecimal at the root and in an object field, binary in a field, a custom serializer writing UTF-8, limit always respected.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
One scalar path (Bounded.scalar): written when its JSON length fits the room left wherever it is (root, field, array), otherwise its text (numbers) or a marker (binary '<N bytes>') goes through writeText, cut; in an array skipped and counted when not even a cut one fits, never after a written field name. Containers only checked in arrays (at the root or in a field, their entries are bounded and the field kept their marker room). BigInteger/BigDecimal: digits estimated from bitLength (BigDecimal + capped scale), beyond the budget a marker '<number of about N digits>' without building the decimal text. UTF-8: UTF-16 length counted from the bytes, at most 4x budget bytes decoded. Raw value / encoded number char arrays copied only up to the budget. Tests: hugeNumbersAndBinariesStayWithinTheLimitWherever, utf8StringsAreCutWithTheirLengthInChars (custom serializer). Validation: mvn -o clean install 197/107/6 (1 skipped); mvn -o clean install -Ppatterns 206 (1 skipped)/145/6 (1 skipped); UI tests 33/33.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Every scalar now fits the room left wherever it sits, or becomes a cut string or marker (a written field always gets a value); huge numbers are measured from their bit length, UTF-8 strings decoded only up to the budget. Verified with new PayloadTruncatorTest cases (17 tests) and both full builds.
<!-- SECTION:FINAL_SUMMARY:END -->
