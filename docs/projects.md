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

## In the apps

Windows shows the four columns side by side as a board, Android the same items as a grouped list,
one group per column with a move action. Android reaches Projects from the bottom bar, beside Today,
Tomorrow, the Inbox and the Calendar, and starts a new project from the + beside its project picker.
Android's Add an item takes the item's type, column, priority and notes; the column follows the type
until one is picked.
Both offer the project list with its status, area, repository
and folder, how many items each project still has waiting, and the milestones of the project on show.
An idea and a bug carry their own icon and colour on the board, so a mixed column reads at a glance.
On Android, moving an item to Done or taking it out of the project offers Undo, as the lists do.

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
