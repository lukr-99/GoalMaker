# M10-04: Adding from the calendar

**Status:** to do · **Milestone:** M10 · **Needs:** M10-02, M10-03

## Scope
- The calendar gets the bottom bar ([composer](../../docs/composer.md)). It adds to the picked day:
  a task by the task rules, planned for that day, or an event, by a Task or Event switch in the bar.
  An event line is its title; its days are the picked ones.
- **Picking several days:** on Android a long-press on a cell starts picking and taps add or remove
  days (dragging across cells picks a run); on Windows Ctrl+click adds a day and Shift+click a run.
  The picked days are outlined and counted in the bar ("3 days").
- With several days picked, the bar asks which: **one event** from the first picked day to the last,
  or **a task on each day** (a copy of the same task planned on each). The choice starts at the one
  used last time on this device.
- Leaving picking (Back, Esc or a tap on one day) goes back to one day.

## Acceptance criteria
- Vectors in `contracts/vectors/calendar.json` (`picked`): what a pick of days makes (an event from
  the first to the last, tasks on each, gaps kept for tasks, the order of the copies).
- Compose and view model tests on both apps: a task on one day, an event on one day, an event across
  a pick, tasks on each of a pick; undo takes back the whole batch.

## Check
- Emulator and Windows: pick three days, add "Prague" as one event, then "Pack" as a task on each.
