# M9-05: The Life goals widget on Android

**Status:** planned · **Milestone:** M9

## Scope
- A Glance widget with one picture of an open life goal at a time, its title and time left, the next
  picture every 30 minutes through all pictures of all open life goals in order; a life goal without
  pictures shows its title and why on the area's color; tapping opens that life goal.
- A picker preview, and the empty state when nothing is open (see `docs/widgets.md`).

## Acceptance criteria
- Tests for which picture shows at a moment; `android/tools/check-widgets.ps1` passes on an
  R8-shrunk dev build.

## Check
- Emulator: the widget shows the Audi R8, moves on, and opens the goal.
