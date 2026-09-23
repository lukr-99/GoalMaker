# GoalMaker

GoalMaker plans days and keeps goals alive across an Android phone and a Windows PC, with Claude as
an optional helper through a connector.

## Language

**Goal**:
An outcome to reach within one period, measured by a progress mode.
_Avoid_: Objective, target (target is a goal's number)

**Horizon**:
The period length a goal belongs to: year, month, week or day.
_Avoid_: Level, scope, timeframe

**Goal cascade**:
Goals linked child to parent across horizons, each parent in a longer horizon than its child.
_Avoid_: Hierarchy, tree (in UI copy)

**Progress mode**:
How a goal measures progress: done/not done, linked tasks, or a number with a target.

**Task**:
A concrete action that can be ticked off, with an optional planned day, deadline, reminders and
repeat rule.
_Avoid_: Todo, item (when a task is meant)

**Planned day**:
The day a task is meant to be done; it decides whether the task shows in Today.
_Avoid_: Do date, scheduled date

**Deadline**:
The date a task must be done by, independent of its planned day.
_Avoid_: Due date (in UI copy)

**Top priority**:
A flag on the few tasks picked as most important for a day, usually during Plan tomorrow.

**Step**:
One line of a task's checklist. Steps are not tasks.
_Avoid_: Subtask

**Inbox**:
The list of tasks with no planned day and no area, where captures wait to be sorted.

**Area**:
A colored life area (Health, School, Work, Personal, ...) that goals, tasks, habits and projects
belong to.
_Avoid_: Category, list

**Tag**:
A free label on any item, used to group across areas.

**Habit**:
A repeating behavior with a cadence and a measure, tracked by check-ins and streaks.
_Avoid_: Routine, recurring goal

**Cadence**:
How often a habit is due: daily, chosen weekdays, N per week or N per month.

**Measure**:
What a habit check-in records: a check, a count with a target, or an amount with a unit.

**Check-in**:
One recorded value for a habit on a date, or a skip.

**Streak**:
The number of consecutive periods in which a habit was met; skipped and paused periods neither count
nor break it.

**Project**:
A code project with a board of project items, an optional repository link and milestones.

**Project item**:
A task inside a project, typed as Task, Idea or Bug, placed in a board column.

**Board column**:
Backlog, To do, Doing or Done.

**Milestone**:
A named group of project items inside one project.

**Made by**:
Who made a task, the owner or Claude, set once when it is made. A board's Made by switch shows
everyone's items, only the owner's, or only Claude's.

**Reminder**:
A notification for a task or a ritual at a time, fired on every device and settled everywhere once
handled.

**Quiet hours**:
The daily window in which ordinary reminders are held back; important reminders pass.

**Ritual**:
A guided flow at a set time: Plan tomorrow, Weekly review or Monthly review.

**Plan tomorrow**:
The evening ritual that settles today's unfinished tasks and plans tomorrow.

**Review**:
A weekly, monthly or yearly ritual that looks back, reflects and sets the next period's goals.

**Reflection**:
A saved answer to a review prompt.

**Prompt library**:
The versioned set of reflection prompts that reviews draw from.

**Day rollover**:
The hour (04:00 by default) at which "today" becomes the next day.

**Composer**:
The input bar at the bottom of the app for quick-add shortcuts and `/` commands.
_Avoid_: Search bar, command palette

**Shortcut**:
A composer token that sets a field: date words, times, `#tag`, `@Area`, `!`, `?`, `+Project`.

**Connector**:
The remote MCP server through which Claude apps read and change GoalMaker data.
_Avoid_: Integration, plugin, bot

**Connector link**:
The secret URL that authorizes the connector in v1; stored only as a hash.

**Activity log**:
The record of every change with its actor (owner, Claude, system), used for undo.

**Replica**:
A device's local copy of the owner's data, kept current by sync.
_Avoid_: Cache (when the full offline copy is meant)

**Outbox**:
The queue of local changes waiting to be pushed to Supabase.

**Tombstone**:
A deleted row kept with `deleted_at` so the deletion reaches other devices.

**Update channel**:
The latest published GitHub Release, holding the release artifacts and the signed release manifest
(ADR 0010).

**Release manifest**:
The signed JSON file describing the latest release and its artifacts' sizes and hashes.
