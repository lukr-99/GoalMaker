# M10-03: Calendar events on Windows

**Status:** to do · **Milestone:** M10 · **Needs:** M10-01

## Scope
- The calendar page draws events as bars across the cells, as on the phone (lanes, area colour,
  "+N" past three lanes, the title on each row's first segment).
- The open day lists its events above the planned tasks; one opens an editor over the page (title,
  first and last day, area, notes, Delete with undo).
- Today shows the ongoing events as a slim line above the tasks.
- Keyboard: each bar is reachable by Tab in reading order and opens with Enter; a screen reader
  reads "Prague, 12 to 15 October".

## Acceptance criteria
- View model tests for the bars, the editor and Today's line; page snapshots of a month with
  overlapping events, wide and narrow.
- `dotnet format`, `dotnet test` and `tools/check_accessibility.py` pass.

## Check
- Running app: the same three-day event across a week break as on the phone; it syncs both ways.
