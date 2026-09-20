# M5-06: Windows mini windows

**Status:** done · **Milestone:** M5

## Scope
- Pinnable mini windows for Today and Habits: small always-on-top windows that remember their place
  and size, opened from the tray, the app, `--mini today|habits` or a `goalmaker://` link (spec,
  stories 80 and 82).
- A mini window ticks tasks and checks habits in like the main window, and follows the theme.
- The single-instance rule already in place hands a second launch's switches to the running copy
  (story 83).

## Acceptance criteria
- Tests for the switch and link parsing and for what each mini window shows; snapshots in every
  theme.
