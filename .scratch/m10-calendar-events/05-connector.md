# M10-05: Events in the connector

**Status:** to do · **Milestone:** M10 · **Needs:** M10-01

## Scope
- `add_event`, `update_event` and `delete_event` (with undo), and events in `get_calendar` (on each
  day, and as a list for the range) and in `get_today` (the ongoing ones, "day N of M").
- The quick chat can add an event: `add_event` joins its short tool list, inside the 16,000-character
  cap.
- The review digest names the events of the week.
- `docs/connector.md` and `docs/assistant.md` list the tools.

## Acceptance criteria
- `deno task lint`, `deno task check`, `deno fmt --check` and the tool tests pass; an endpoint test
  adds, reads, moves and deletes an event, and undoes the delete.
