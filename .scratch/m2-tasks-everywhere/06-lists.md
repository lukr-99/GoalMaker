# M2-06: Today, Tomorrow and Inbox

**Status:** doing · **Milestone:** M2

## Scope
- The list rules in `docs/lists.md` with `contracts/vectors/lists.json` (Today's sections, Tomorrow,
  Inbox, the day's summary, the day starting at 04:00), run by both apps.
- Today laid out per docs/design/spec.md; Tomorrow; Inbox. The screen you type on supplies the day
  when the composer line doesn't (docs/composer.md).
- The day's start is a setting (00:00 to 06:00).
- Completing and deleting with undo, the check animation, the optional completion tick.
- Phone: bottom navigation. Windows: sidebar entries, collapsible to icons, remembered.

## Acceptance criteria
- Items move between lists as their day or area changes, on both apps, in sync.
- Both apps pass the list vectors.
