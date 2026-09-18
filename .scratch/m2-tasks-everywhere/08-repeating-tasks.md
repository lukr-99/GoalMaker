# M2-08: Repeating tasks

**Status:** todo · **Milestone:** M2

## Scope
- Presets (daily, weekdays, weekly on chosen days, every N days, monthly on a date) stored as an
  RRULE subset; the next occurrence appears when the current one is done or skipped.
- `contracts/vectors/recurrence.json` for expansion, run by both apps.

## Acceptance criteria
- A series never shows two open occurrences, on either device, after offline edits on both.
