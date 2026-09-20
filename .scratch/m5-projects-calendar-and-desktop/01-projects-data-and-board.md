# M5-01: Projects: data and the board rules

**Status:** done · **Milestone:** M5

## Scope
- Migration and replica migration: `projects` (name, description, area, status active, paused or
  done, repository URL, local folder, notes, position) and `project_milestones` (name, position),
  synced like every table, plus the project columns on `tasks` the data model names: `project_id`,
  item type (task, idea or bug), board column (backlog, todo, doing or done), priority (low, normal,
  high or urgent) and `milestone_id` (spec, stories 43 to 50; data model).
- The board rules, pinned by `contracts/vectors/projects.json` and run by Kotlin, C# and the
  connector: where a new item lands (an idea in Backlog, story 49), how the board column and the
  task's own state move together (the Done column means the task is done, and finishing a task moves
  it to Done), the priority order, grouping by milestone, and which project items Today shows
  (story 50: a planned day, whatever their column).

## Acceptance criteria
- pgTAP row security and checks for both tables and the new columns; the migration harness passes.
- Every vector case passes in all three implementations.

## Result
- Supabase migration 0013 (`projects`, `project_milestones`, the five task columns, 16 pgTAP checks,
  fixtures) and replica migration 0008, with the synced-tables contract naming projects before tasks.
- A project that is purged leaves its items behind as plain tasks: Postgres can only null the columns
  of the foreign key itself, so a `before delete` trigger clears the project, the column and the
  milestone together, which the item check constraint needs.
- `contracts/vectors/projects.json` with `ProjectRules` in Kotlin and C# and `rules/projects.ts` in
  the connector; all three pass. docs/projects.md.
- Left for M5-02: reading and writing projects in the apps (a `ProjectList` beside `TaskList`), and
  the screens.
