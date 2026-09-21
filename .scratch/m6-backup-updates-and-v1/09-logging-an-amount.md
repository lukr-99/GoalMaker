# M6-09: Logging an amount without leaving the page

**Status:** done (Windows) · **Milestone:** M6

## Scope
- A habit that counts or measures something (8 glasses, 30 minutes) is checked in by tapping its
  ring, which opens a panel that takes the whole page for one number. On a window with room, that is
  far more ceremony than the act deserves.
- Windows, where there is space: the row itself offers the amount. A plus that adds one step (a
  glass, five minutes), and a small field for a number when the owner wants to say exactly. The
  panel stays for the narrow window and for the habits that need more than a number.
- Android keeps the panel for now; the phone has no room for the row form. Revisit with the phone in
  hand.
- Whatever the way in, one check-in is one row for that habit and day, as the rules already say
  (docs/habits.md), so adding 3 then 5 is 8, not two rows.

## Acceptance criteria
- A view-model test: adding from the row logs the same check-in the panel does, and the ring and the
  streak move with it.
- The row form appears only when the window has room for it, and the panel still works when it does
  not.

## Done

2026-09-21, Windows. A habit that counts or measures takes its amount in the row: a plus for one
more, and a small field with Enter or a tick for a number. The panel stays, reached by the ring or
the menu, and the mini window keeps its own shorter row. Android keeps the panel: the phone has no
room for a field beside the ring, and the ring is already one tap away.
