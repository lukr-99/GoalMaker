# M5-02: Projects in both apps

**Status:** done · **Milestone:** M5

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

## Result
- `ProjectList` on both apps reads and writes projects and their milestones; `TaskList` moves an item
  around the board (`setProject`, `setBoardColumn`, `setItemType`, `setPriority`, `setMilestone`), and
  finishing or reopening a task moves its card with it.
- Android: Projects from the overflow menu on Today, the projects as chips, the one on show with its
  repository and folder, and the four columns as sections with a menu per item.
- Windows: a Projects page in the sidebar (or `--open projects`), the projects on the left and the
  board beside them, with a box to add a task, an idea or a bug.
- Tests: `ProjectListTest` on Android (8 cases over a real replica) and `ProjectsViewModelTests` on
  Windows (6), plus a rendered snapshot of the page.
- Left for later: dragging cards between columns (both apps move them from a menu for now), and the
  milestone picker on an item (the data and the rules carry milestones already).
