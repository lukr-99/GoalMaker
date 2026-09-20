# M5-03: The calendar

**Status:** done · **Milestone:** M5

## Scope
- A week and a month view of planned tasks, deadlines and reminders on both apps, with a day opening
  its tasks and a task moving to another day from the calendar (spec, story 68).
- What lands on a day is a shared rule pinned by `contracts/vectors/calendar.json`: the planned day,
  a deadline shown on its own day, a reminder at its local time, repeating occurrences inside the
  range, and the owner's planning day for "today".
- Android uses Kizitonwose Calendar, Windows draws the grid itself; both follow the design spec.

## Acceptance criteria
- Every vector case passes in both apps; view-model tests for the week and the month.

## Result
- `CalendarRules` and `CalendarDay` on both apps, pinned by `contracts/vectors/calendar.json`: the
  week and month grids (whole weeks, Monday first) and what each day holds: planned tasks, deadlines,
  how many reminders ring, and the days a repeating task would come round to.
- Repeats are worked out from the rule rather than from rows, since only the current occurrence
  exists: never on the day the task is already planned for, and never where another occurrence of the
  same series is planned.
- Android reaches the calendar from the overflow menu on Today, Windows from the sidebar or
  `--open calendar`; both draw the grid with a bar that grows with the day, and picking a day lists
  what is on it. Tests: the contract cases in both apps and `CalendarViewModelTests` on Windows, with
  a rendered snapshot of the page.
- Dragging a planned task from the day's list onto another cell moves it (the owner asked): Compose
  drag and drop on Android with the cell lighting up under it, and DragDrop in the page's code-behind
  on Windows. Deadlines and projected repeats stay put.
- Left for later: showing a reminder's own time in the day's list.
