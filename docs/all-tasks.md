# All tasks

All tasks is the place that shows every open task in one list. Today, Tomorrow and the Inbox each
hold a slice ([lists](lists.md)): a task with an area but no day is in none of them, so before this place
existed, filing an Inbox task into an area made it vanish from view. The rules below are pinned by
[`contracts/vectors/all-tasks.json`](../contracts/vectors/all-tasks.json) and run by both apps and
the connector.

## What it shows

- Every open task that isn't deleted, whatever its day: overdue, today, later or none.
- **Grouped by area**, in the order the area pickers use, with the tasks without an area last. A
  project item without an area of its own counts as being in its project's area. An area that was
  deleted counts as none, and an empty group is left out.
- **Inside a group**, by date (the deadline, else the planned day), earliest first and undated last,
  then by title, then by id. A row shows the date, "Due" before a deadline, in red once it has passed.
- **Project items** stay out unless the Project items switch is on: their boards list them already,
  and there can be hundreds of them.

## Finding a task

- The **search box** keeps the tasks with every typed word in their title or notes, ignoring case and
  accents, the same rule as the archive and the Focus Board's add panel.
- The **area and tag filter** is the one the lists use ([lists](lists.md)), with a filter of the
  place's own, so narrowing here never hides anything elsewhere.
- Tapping a task (Android) or its open button (Windows) opens it; ticking it (Android) or Complete
  (Windows) completes it, and it leaves the list.

## Through Claude

`get_all_tasks` lists the same groups ([connector](connector.md)), narrowed to one area by name or to
words, and with project items when asked.
