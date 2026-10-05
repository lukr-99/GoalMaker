# M10-01: Calendar events: data and rules

**Status:** to do · **Milestone:** M10

## Scope
- Supabase migration 0026: `events` (title 1 to 200 characters, `starts_on`, `ends_on` on or after
  it and at most 366 days later, notes up to 10,000 characters, area, made by) with the usual synced
  columns, row security, the activity log trigger, undo, the purge, grants and the Realtime
  publication.
- Replica migration 0019 and the table in `contracts/schemas/synced-tables.json`; the backup carries
  it in the synced-tables order.
- `CalendarRules` in Kotlin, C# and `rules/calendar.ts` learn events: which events a day holds, and
  the bars a week row draws.
- `EventList` in both apps over the replica: add, edit, delete (with undo), and the events of a range.

## Rules
- **A day's events:** every event whose first day is on or before it and whose last day is on or
  after it, not deleted, narrowed by the area filter like tasks (an event has no tags, so a tag
  filter hides every event). Order: earliest first day, then the longest, then title, then id.
- **Bars:** for each week row of a grid, a segment per event that touches the row: its first and
  last column inside the row, whether it goes on before the row or after it, and its lane. Lanes are
  given greedily over the whole grid in the day order above, so an event keeps one lane from row to
  row; a later event takes the lowest lane free on all of its days.
- **Ongoing on Today:** the events the planning day falls inside, with "day N of M" when an event is
  longer than one day.

## Acceptance criteria
- pgTAP: owner can, stranger can't, anonymous can't; the checks (title length, last day not before
  the first, at most 366 days). The migration harness passes in full and as 0025 to 0026 with
  fixtures; `tools/check_synced_tables.py` passes.
- Every new `calendar.json` case passes in Kotlin, C# and TypeScript.
- A backup round trip keeps events.

## Vectors to add
- `contracts/vectors/calendar.json`: `days` cases with events (one day, across a month end, a
  filtered area, a deleted one), a `bars` group (one row, across rows, two that overlap, a lane freed
  and taken again, an event that starts before the grid), and an `ongoing` group (day 1 of 4, the
  last day, a one-day event without "day N of M").
- `contracts/vectors/backup.json`: the table order with `events`.
