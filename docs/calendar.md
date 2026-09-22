# The calendar

The calendar is the plan across days (spec, story 68): a **week** or a **month** of planned tasks,
deadlines and reminders. The rules below are pinned by
[`contracts/vectors/calendar.json`](../contracts/vectors/calendar.json) and run by both apps.

## The grid

A week runs from its **Monday** to its **Sunday**. A month runs from the Monday on or before its
first day to the Sunday on or after its last, so a month is always whole weeks: September 2026 runs
from Monday 31 August to Sunday 4 October, five rows of seven.

## What lands on a day

| Line | What it is |
|---|---|
| Planned | the tasks planned for that day, deleted and dropped ones left out |
| Due | the tasks whose deadline is that day and that are still open |
| Reminders | how many reminders ring that day ([reminders](reminders.md)) |
| Repeats | the days a repeating task would come round to |

Within a day, the earliest time comes first, tasks without a time follow, then the oldest and the id,
the same order Today uses ([lists](lists.md)).

**Repeats** are worked out from the task's rule ([repeating](repeating.md)), not from rows: only the
current occurrence exists until it is finished, so the calendar shows where the next ones would fall.
A repeat is only projected for a task that is still open, never onto the day it is already planned
for, and never onto a day another occurrence of the same series is planned for, so a task that has
already moved on is never drawn twice.

## In the apps

Android reaches the calendar from the bottom bar, beside Today, Tomorrow, the Inbox and Projects,
Windows from the sidebar or `--open calendar`. Both draw the month as a grid of day cells with a bar
that grows with what the day holds, today outlined and the day the owner picked filled; picking a day
lists what is on it, and a task opens from there. The week view is the same grid, one row.

**Moving a task** is a drag: hold a planned task in the day's list and drop it on another cell, and
it is planned for that day, moves counted like any other move ([plan tomorrow](plan-tomorrow.md)).
The cell lights up while a task hangs over it. Deadlines and projected repeats are not dragged: a
deadline belongs to its task and a repeat has no row of its own yet.
