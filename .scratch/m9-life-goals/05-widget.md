# M9-05: The Life goals widget on Android

**Status:** built, waiting for review · **Milestone:** M9

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

## Result

2026-10-04.

- `LifeGoalsWidget` and its receiver, `WidgetKind.LIFE_GOALS`, a picker preview, and the slide rule in
  `WidgetContent` (`WidgetLifeGoalsTest`: every picture of every open life goal in order, one slide
  for a life goal without pictures, the next every half hour round and round).
- The receiver turns the launcher's half-hourly update into a redraw, because an update alone keeps
  a running session's slide. The widget redraws when a picture file comes down.
- **Checked:** `check-widgets.ps1` on the emulator with the R8-shrunk dev build: the dex check passes
  (no widget classes merged), the widget shows the Audi R8 picture with "10 years left", and a tap
  opens Life goals. The half-hourly change was not watched on the device.
