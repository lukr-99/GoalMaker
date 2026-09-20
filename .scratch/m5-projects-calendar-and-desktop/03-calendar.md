# M5-03: The calendar

**Status:** todo · **Milestone:** M5

## Scope
- A week and a month view of planned tasks, deadlines and reminders on both apps, with a day opening
  its tasks and a task moving to another day from the calendar (spec, story 68).
- What lands on a day is a shared rule pinned by `contracts/vectors/calendar.json`: the planned day,
  a deadline shown on its own day, a reminder at its local time, repeating occurrences inside the
  range, and the owner's planning day for "today".
- Android uses Kizitonwose Calendar, Windows draws the grid itself; both follow the design spec.

## Acceptance criteria
- Every vector case passes in both apps; view-model tests for the week and the month.
