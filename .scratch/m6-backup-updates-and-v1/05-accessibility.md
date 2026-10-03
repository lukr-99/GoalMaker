# M6-05: The accessibility pass

**Status:** closed for v1 · **Milestone:** M6

## Scope
- Every screen on both apps read by a screen reader (TalkBack, Narrator): a name for every control
  that does something, no name for what is decoration, and rows that read as one thing rather than
  five (a task's title, its day, its area and its done box).
- Focus and order: a keyboard can reach and use everything on Windows, including the mini windows,
  the tray flyout and the quick-add box; Android moves focus in reading order and the composer's
  chips are reachable.
- Size and contrast: the four themes checked against the contrast the design spec asks for
  (docs/design/spec.md), and both apps usable at the largest system text size without losing
  controls off the edge.
- Motion: the reduce-motion setting already in place is honoured by every animation added since M2,
  including the confetti, the rings and the calendar's drag.

## Acceptance criteria
- Compose semantics tests for the task row, the composer, the habit ring and the calendar cell;
  Windows automation-name tests for the same places.
- A checklist per screen in `docs/design/accessibility.md`, with what was fixed and what was left.

## Progress

2026-09-21. Names: every interactive control in both apps already had something to read, which
`tools/check_accessibility.py` now proves on every run and in CI, so it stays that way. Rows: a task
row and a habit row read as one thing on Android instead of three or four, by merging the text column
rather than the whole row, so the checkbox and the ring stay their own controls.

Left: keyboard reach on Windows (the mini windows, the tray flyout, the quick-add box), Android focus
order and the composer's chips, and both apps at the largest system text size. Those need the apps in
hand rather than a scan.

## Close-out (2026-09-28)

GoalMaker is for one person (ADR 0011), so the rest of the pass moves to "After v1" in the roadmap:
keyboard reach on Windows (mini windows, tray flyout, quick-add), Android focus order and the
composer's chips, and both apps at the largest text size. What was done stays enforced by
tools/check_accessibility.py.

2026-10-03, after v1. The rest of the pass is done; the per-screen checklist, with what was fixed and
what is left, is docs/design/accessibility.md.
- Windows keyboard: the tray icon opens its flyout (Enter or Space) and menu (Shift+F10) from the
  keyboard, which it could not before; the flyout takes the keyboard, cycles, and Esc hands it back to
  the notification area. Mini windows start the keyboard on the first task or check-in, close on Esc
  only once the composer's line is empty, and hand the keyboard back. Tab now reads in screen order
  in the composer, task rows, the list toolbar and every DockPanel that jumped, and
  tools/check_accessibility.py fails on a new one. Tests walk WPF's own Tab order on hidden windows.
- Android: composer chips read as one button each ("Tomorrow, Remove"), task rows and habit cards as
  one item with their actions, calendar cells say their date and what is on them (Windows too). The
  first Compose UI tests (Robolectric) cover the chips, task row, habit card, calendar cell, settings
  rows and navigation bar.
- Largest text: Android fixed heights became minimums where text sits (calendar cells, check-in,
  settings rows, chips, sign-in) and sheets scroll, tested at font scale 2. Windows now follows its
  text size at all (Theming/TextScale scales each window's content and grows the windows), tested at
  225%.
- Left: nobody has listened to TalkBack or Narrator on a device; calendar moves are drag and drop
  only; mini windows move and resize by mouse only; on Android two list animations ignore reduce
  motion.
