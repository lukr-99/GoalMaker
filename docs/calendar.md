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

## Events

An **event** takes up days rather than gets done: a trip, a holiday, a conference (spec, stories 120
to 123; ADR 0019). It has a title, a first and a last day (the same day for a one-day event, at most
366 days apart), and maybe notes and an area. It is never ticked off. The rules are pinned by the
`eventDays`, `bars` and `ongoing` groups of
[`contracts/vectors/calendar.json`](../contracts/vectors/calendar.json), run by both apps and (all but
the bars) the connector.

- **A day's events** are every event whose first day is on or before it and whose last day is on or
  after it. They come earliest first day first, then the longest, then by title. The area filter
  keeps an area's events; a tag filter hides them all, since events have no tags.
- **Bars:** the grid draws an event as one bar across its days, a piece per week row. Each event gets
  a lane, the lowest one free on all of its days, and keeps it from row to row, so a trip across a
  weekend stays on one line.
- **Going on:** Today shows the events the planning day falls inside, with "day 2 of 4" for one longer
  than a day.

In the apps the bars sit over the cells of each week row, in the area's colour (the accent without
one), with the title on each row's piece and square ends where the event goes on past the row. A
cell shows three lanes at most, then "+N". The open day lists its events above its tasks; one opens
the event sheet (title, days, area, notes, Delete with undo). Today's line above the tasks opens the
same sheet, and like the calendar it follows the lists' area and tag filter. A screen reader reads a
bar as "Prague, 12 to 15 October", and a cell's name counts its events.

## In the apps

Android reaches the calendar from the bottom bar, beside Today, Tomorrow, the Inbox and Projects,
Windows from the sidebar or `--open calendar`. Both draw the month as a grid of day cells with a bar
that grows with what the day holds, today outlined and the day the owner picked filled; picking a day
lists what is on it, and a task opens from there. The week view is the same grid, one row.

**Filtering**: the lists' area and tag filter ([lists](lists.md#filtering)), with a filter of its
own, sits over the grid on both apps. It narrows everything a day holds, the planned tasks,
deadlines, repeats and reminders, so the cells' bars and the day's list show the same slice, and a
project item without an area of its own counts as in its project's. A repeat is projected only for a
task the filter keeps, but an occurrence the filter hides still keeps its day from being drawn as a
repeat. Pinned by the filtered case in
[`contracts/vectors/calendar.json`](../contracts/vectors/calendar.json).

**Moving a task** is a drag: hold a planned task in the day's list and drop it on another cell, and
it is planned for that day, moves counted like any other move ([plan tomorrow](plan-tomorrow.md)).
The cell lights up while a task hangs over it. Deadlines and projected repeats are not dragged: a
deadline belongs to its task and a repeat has no row of its own yet.

## Adding from the calendar

The calendar has the bottom bar ([composer](composer.md)), which adds to the picked day: a **task**,
read by the task rules and planned for that day, or an **event**, by the bar's Task or Event switch.
An event's line is its title; its days are the picked ones. On one day a task line that names its own
day keeps it, as on Tomorrow, and with nothing picked the bar adds to today. In Event mode the empty
bar's plus opens the event editor on the picked days.

**Several days** can be picked at once: on Android a long-press on a cell starts picking and taps add
or take away days; on Windows Ctrl+click (Ctrl+Space on a focused cell) adds or takes away a day and Shift+click
(Shift+Space) adds the run from the last picked day. The picked days are
outlined and the bar counts them ("3 days"). With several picked, the bar asks which to make: **one
event** from the first picked day to the last, gaps included, or **a task on each day**, a copy of
the same task planned on each. The copies don't repeat, even when the line names a repeat. It starts at the choice used last on that device. Undo takes back the
whole batch. Pinned by the `picked` group of
[`contracts/vectors/calendar.json`](../contracts/vectors/calendar.json). Leaving picking (Back, Esc,
or a plain tap on one day) goes back to one day.

## Putting a day right

The open day is also where a day gone by is put right (the owner's board item "able to finish
task/habits in retrospective from calendar"):

- **Tasks:** a planned task or a deadline on the open day has a done box. Ticking it off on a day
  gone by stamps that day's noon as when it was done, so the stats and the archive count it on that
  day; on today or a day to come it is done now. Unticking opens it again. A repeat has no row of its
  own yet, so it has no box.
- **Habits:** on today or a day gone by, the day lists every habit due on it as a card, the way Today
  shows them, read as of that day. Its button and menu check in, add one, log an amount, skip, fail or
  clear on that day, so a run that was never logged still counts toward the streak. A day to come
  lists none.
