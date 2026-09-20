# M5-02: Projects in both apps

**Status:** todo · **Milestone:** M5

## Scope
- Windows: a Projects page with the project list (status, area, repository, folder) and the
  Backlog / To do / Doing / Done board for the project on show, items moving between columns, with
  item type, priority and milestone on each card (spec, stories 43 to 48).
- Android: the same projects as a grouped list, one group per column, with a move action per item.
- Create and edit a project and its items on both apps; an item with a planned day turns up in Today
  next to ordinary tasks (story 50), and the task detail shows which project an item belongs to.
- Areas and tags work on project items exactly as on tasks.

## Acceptance criteria
- View-model tests over a real replica on both apps; a move made on one app shows on the other.
- The board and the grouped list render in every theme and mode.
