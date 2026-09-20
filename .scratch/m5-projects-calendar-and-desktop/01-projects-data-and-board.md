# M5-01: Projects: data and the board rules

**Status:** todo · **Milestone:** M5

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
