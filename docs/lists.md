# Lists

Which open tasks each list shows, and in what order. Pure rules, pinned by
[`contracts/vectors/lists.json`](../contracts/vectors/lists.json) and run by both apps.

**The planning day** is the local date shifted back by the day's start hour (04:00 by default, a
setting from 00:00 to 06:00): at 01:30 it is still yesterday. Every list below uses it.

Only open tasks appear; done, dropped and deleted ones leave every list.

| List | Holds | Order |
|---|---|---|
| Today: top priorities | top priority, planned today | time (untimed last), then creation |
| Today: scheduled | planned today with a time, not top priority | time, then creation |
| Today: more | planned today without a time, not top priority | creation |
| Today: overdue | planned before today | day, time (untimed last), creation |
| Tomorrow | planned for tomorrow | top priority first, then time (untimed last), creation |
| Inbox | no planned day, no area and no project | creation |

Tasks planned after tomorrow, and undated tasks with an area, wait in their area (M2-11) and the
calendar (M5). An undated item of a project waits on that project's board (M5), because the board is
where it was filed; it reaches the Inbox only once it is taken out of the project. Ties always break
by id, so both apps agree.

**The day's summary** ("2 of 5 done") counts tasks planned for today that are open or done; dropped
and deleted ones don't count.

## Project items in a list

A project item is a task like any other in the lists ([projects](projects.md)), so a row says which
project it belongs to: next to the area on the line under the title it wears a small outlined chip
with the project's name, a lightbulb for an idea and a bug for a bug in the board's colours, and the
board's own icon for a plain task. The chip is on Today, Tomorrow, the Inbox, the Windows Today mini
window, the calendar's open day and the archive, and on both apps it opens that project's board. A
screen reader hears it as "Project GoalMaker", "Idea for GoalMaker" or "Bug in GoalMaker", and the
open action as "Open the GoalMaker board"; on the phone the row also offers that as one of its
TalkBack actions, and on Windows the done box's help text names the project too. The Today widget
names the project in small muted text after the title. An item of a deleted project wears no chip,
the same way [stats](stats.md) counts it as other work.

## Filtering

Any list can be narrowed to one area, one tag, or both (spec, story 9). The filter picks the tasks
first and the rules above run on what is left, so the sections, the order and the day's summary all
describe the same slice. A task matches an area when it belongs to it, and a tag when the tag is
linked to it; with both set it has to match both. A project item without an area of its own counts as
being in its project's area, so a Work project's items show under Work; an item's own area always
wins over its project's. The Inbox holds tasks without an area or project, so it is empty under an
area filter. The filter belongs to the screen, not to one list, so it stays when the owner switches
between Today, Tomorrow and the Inbox. Pinned by the `filter` cases in
[`contracts/vectors/lists.json`](../contracts/vectors/lists.json).

The same filter narrows the Projects board ([projects](projects.md)), the calendar
([calendar](calendar.md)) and the archive ([archive](archive.md)), with the same pickers. Each of
those places keeps a filter of its own while the app runs, apart from the lists' one, so narrowing
the calendar to Work never hides anything in Today.

The pickers sit above the list on both apps: a chip row on the phone, and on Windows an area and a
tag picker on the toolbar line under the day, next to the sync state and Plan tomorrow. A chosen area
or tag is outlined in the theme's accent, so a narrowed list is plain to see. (Windows had them at
the foot of the sidebar at first; the owner found them hidden there, 2026-09-19.)

Deleting an area keeps its tasks and takes the area off them, so an undated one returns to the
Inbox. Deleting a tag takes it off every task.

**Archiving** an area keeps everything: its tasks keep the area and show its chip, and it keeps its
color and name. It only leaves the pickers and the filters, and a filter on it falls away. Naming it
again (`@Garden` in the composer) brings it back, and so does Restore in the areas manager, where
archived areas are listed apart. The synced `areas.archived_at` says when it was archived.
