# Projects

A project is where long-running work lives: code projects above all, but anything with its own
backlog (spec, stories 43 to 50). The rules below are pinned by
[`contracts/vectors/projects.json`](../contracts/vectors/projects.json) and run by both apps and the
connector.

## A project

A project has a name, a description, an **area**, a **status** (active, paused or done), the
**repository URL** and the **local folder** it lives in, and notes. The repository and the folder are
what let a tool find the right project: Claude Code working in `F:\GoalMaker` can drop an idea into
this project's backlog without being told which one it is (story 76, [connector](connector.md)).

**Milestones** are optional and belong to one project: M0 to M6, or whatever the owner calls them.
An item may carry one.

## Items are tasks

A project item is an ordinary task with project columns on it, not a thing of its own. It keeps its
area, tags, steps, reminders and repeat, and a project item with a planned day turns up in Today next
to everything else (story 50). Every task, in or out of a project, carries a **priority**: low,
normal, high or urgent.

An item is typed **task**, **idea** or **bug**, and sits in one of four **board columns**:

| Column | What it holds |
|---|---|
| Backlog | Everything caught but not chosen yet. New ideas land here (story 49). |
| To do | Chosen for soon. New tasks and bugs land here. |
| Doing | In hand now. |
| Done | Finished. |

The column and the task's own state move together, so a board and a list never disagree:

- Moving an item to **Done** completes the task, exactly as ticking it does.
- Completing a task that is a project item moves it to **Done**.
- Moving a done item back to any other column reopens it.
- Dropping a task leaves its column alone: a dropped item stays where it was, greyed out.

Items are ordered inside a column by priority (urgent, high, normal, low), then by the position the
owner dragged them to, then by when they were created.

## Done items leave the board

A done item leaves the board a number of days after the planning day it was finished: the project's
own number (Supabase `projects.archive_after_days`, 1 to 365), 14 unless the owner changed it, or
never. Any done item can also be archived by hand (`tasks.board_archived_at`). Leaving the board is
only about the board: the task stays done, in the archive, in search and in stats, and the end of
Done says how many left, with a way to put one back. Reopening an item, dropping it or taking it out
of its project brings it back; the server clears the mark itself, so an app from before this change
can reopen one too. The rule is pinned by the `archive` group of `contracts/vectors/projects.json`
(migration 0018).

On the phone the board has two views, switched in its header and remembered on the device: one
column at a time with tabs and counts, or the columns stacked with each section folding away (Done
folded at first). On Windows the columns stay side by side, and each can fold to a narrow strip with
its name and count. A done item's menu has Archive, with Undo. In the list of archived items, one
archived by hand can be put back, and one that left with time can be reopened into To do. The
project's edit form sets the days: 7, 14, 30, 90 or never.

## Who made an item

Every task says who made it: **the owner** or **Claude**. Anything typed into an app is the owner's.
Claude's items come through the [connector](connector.md), and there Claude says which is which: an
item the owner asked for ("add an idea: dark mode for the widget") is the owner's, and something
Claude adds on its own, like a bug it found while working in a repository, is Claude's. When Claude
doesn't say, the item is Claude's, the same as the activity log records it.

It is set once, when the task is made, and nothing changes it after: not an edit, not an undo, not a
repeated sync push. A repeating task's next occurrence keeps who made the series. The rule is pinned
by the `makers` section of [`contracts/vectors/projects.json`](../contracts/vectors/projects.json).

A board has a **Made by** switch: **Everyone** (the default), **Me**, or **Claude**. It only filters
what the board shows; the project list's counts still count every item. On a board, an item Claude
made says "by Claude" in small print.

## Filtering by area and tag

Projects has the lists' area and tag filter ([lists](lists.md#filtering)), with a filter of its own.
A project's own area counts like this:

- **On the board**, an item without an area of its own counts as being in its project's area, and an
  item's own area wins. So under Work, a Work project shows the items with no area and the ones filed
  under Work, but not one filed under Home.
- **In the project list**, a project stays while its own area is the one chosen and no tag is, or
  while the filter keeps at least one of its items. So under Home, a Work project holding one item
  filed under Home stays, showing just that item; under a tag, only the projects holding a tagged
  item stay, and a project's area alone is not enough once a tag is chosen.

The project on show moves to the first one the filter keeps, and when it keeps none the page says
so, with the filter still there to let go. Like Made by, the filter leaves the project list's counts
alone. It works with Made by: an item has to pass both. The rule is pinned by the `filter` cases
with `projects` in [`contracts/vectors/lists.json`](../contracts/vectors/lists.json). The pickers sit
over the board on Windows and above the project picker on the phone. A project's area is set in its
edit form on both apps, and a new project made while an area filter is on starts in it.

## In the apps

Windows shows the four columns side by side as a board. Android shows one column at a time under
tabs, or all four as a list with a section per column, and moves an item from its menu. Android reaches Projects from the bottom bar, beside Today,
Tomorrow, the Inbox and the Calendar, and starts a new project from the + beside its project picker.
Both offer the project list with its status, area, repository
and folder, how many items each project still has waiting, and the milestones of the project on show.
An idea and a bug carry their own icon and colour on the board, so a mixed column reads at a glance.

In both apps a new item takes its type, column, priority and notes; the column follows the type
until one is picked. Moving an item to Done or taking it out of the project offers Undo, as the
lists do. Each project's status carries a mark of its own, in the list or picker and on the card:
active plays on in the accent, paused and done step back in the muted colour.

## Through the connector

Claude reads the projects and one project's board (all of it, or only the owner's items or only
Claude's), adds and edits items with their type, priority, milestone, column and who made them, and
moves an item between columns, over these same rules
([connector](connector.md)). It names the project by id, repository URL, the folder it is working in
or the project's name, so Claude Code drops an idea into the right backlog without being told which
one it is.

Claude also makes a project, with its milestones in one go, adds a milestone to one that already
exists, renames and removes milestones, changes a project's own fields including its status, and
deletes a project, which leaves its items behind as plain tasks. A new project's name, repository and folder each have to be free, because those are what the
match above reads and two projects sharing one would make it a toss-up. A folder sitting inside
another project's folder is fine, since the deepest folder wins.

The board Claude reads leaves out the done items that left it and says how many; `update_project`
sets how long done items stay (`archive_after_days`, or null for only by hand), and
`update_project_item` with `archived` takes a done item off the board or puts it back.

## Storage

`projects` and `project_milestones` are synced tables (Supabase migration 0013, replica migration
0008), and `tasks` gained `project_id`, `item_type`, `board_column`, `priority` and `milestone_id`.
A task with no project has no column and no milestone, which a check constraint keeps true, and a
milestone has to belong to the item's own project.

`tasks.made_by` came with Supabase migration 0015 and replica migration 0010. The server fills it in
when a new row leaves it empty (Claude through the connector, the owner otherwise) and keeps the old
value on every update. The tasks from before it were filled in from the activity log's records of who
created them. Because the rows already on a device can't know, replica migration 0010 forgets the
tasks watermark, so the next sync takes every task fresh from the server ([sync](sync.md)). Deleting a project leaves its items behind as
plain tasks rather than taking them with it.
