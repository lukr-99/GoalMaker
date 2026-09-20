# M5-08: Projects through the connector

**Status:** done · **Milestone:** M5

## Scope
- Tools to read projects and their boards, add and edit items with type, priority, column and
  milestone, and move an item between columns (spec, stories 70, 71 and 76), over the same rules the
  apps run.
- A tool that finds the project by its repository URL or local folder, so Claude Code can drop an
  idea or a bug into the right backlog from the folder it is working in (story 76).

## Acceptance criteria
- Endpoint tests on the local stack; an item Claude adds shows in both apps with "by Claude".
