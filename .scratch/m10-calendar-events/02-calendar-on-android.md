# M10-02: Calendar events on Android

**Status:** to do · **Milestone:** M10 · **Needs:** M10-01

## Scope
- The month and week grids draw each event as a bar across its days, in its lane, in the area's
  colour (the accent without one), with the title on the first segment of each row. Cells keep
  their task bar under the event bars; at most three lanes show, then "+N".
- The open day lists its events above the planned tasks. Tapping one opens the event sheet: title,
  first and last day (a range picker), area and notes, with Delete and an undo snackbar.
- Today shows the ongoing events as a slim line above the tasks ("Prague · day 2 of 4"), which opens
  the sheet.
- TalkBack reads a bar as "Prague, 12 to 15 October"; a cell's description counts its events.

## Acceptance criteria
- Compose tests: a bar spans the right cells and lanes; the sheet saves, edits and deletes; Today's
  line shows only while the event lasts.
- Lint and the accessibility check pass.

## Check
- Emulator: a three-day event across a week break draws two segments in one lane; editing its days
  moves the bar; deleting it and undoing brings it back.
