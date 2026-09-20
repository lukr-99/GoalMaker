# M6-05: The accessibility pass

**Status:** todo · **Milestone:** M6

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
