# M3-05: Tools that change things

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- Add a task (title, day, time, deadline, area by name, tags, top priority, notes, repeat), edit
  it, complete or reopen it (a repeating task moves on like in the apps), move it to a day or the
  Inbox, set and remove reminders, add and check steps (spec, story 71).
- Delete is soft and needs `confirmed: true`, which the tool description tells Claude to ask the
  owner for first; restore brings it back (spec, story 73).
- Every change goes through the owner-scoped runner, so the activity log says "claude" (story 72),
  and the apps pick it up through normal sync.

## Acceptance criteria
- Endpoint tests: each tool's change lands in the table, is logged with the claude actor, and a
  delete without confirmation changes nothing.
- A task added through the connector shows up on both apps after a sync.

## Result
- `add_task`, `update_task`, `complete_task`, `drop_task`, `reopen_task`, `move_task`,
  `delete_task` (refused without `confirmed: true`), `restore_task`, `add_reminder`,
  `remove_reminder`, `add_step`, `check_step`, over `_shared/planner/planner.ts`, which does what the
  apps' task, area and tag lists do (a repeating task moves on with the successor id; a new area gets
  the next free palette color; an archived one comes back).
- Checked on the local stack: a task Claude added through the link showed on the PC's Today and the
  phone's, as "Claude added" in Activity on both.
