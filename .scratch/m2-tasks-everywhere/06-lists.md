# M2-06: Today, Tomorrow and Inbox

**Status:** done 2026-09-18 · **Milestone:** M2

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

## Result
- Both apps pass the 13 list vectors. Today shows top priorities, scheduled (by time), more today,
  and overdue folded with its dates; Tomorrow puts top priorities first, then time; the Inbox holds
  tasks with no day and no area. Headers only appear when there is more than one kind of task.
- Each list's composer puts a line without a day on that list's day (Today, tomorrow, none).
- Completing shows the check for 400 ms (skipped with reduced motion), plays the optional tick, and
  offers undo for 5 seconds; so does deleting (swipe on the phone, the bin on Windows).
- The day's start is in Settings > Planning (00:00 to 06:00); the lists move on at that hour.
- Phone: bottom navigation. Windows: Today, Tomorrow and Inbox in the sidebar, which remembers
  being collapsed to icons.
- Verified: on the phone by hand on the emulator (sections, Tomorrow's default day, complete and
  delete with undo through sync, Inbox, the slider). On Windows by headless view-model tests
  (sections, default days, complete and delete with undo, overdue folding, the start hour) and
  window captures of all three lists, Settings and the collapsed sidebar; a task planned for
  tomorrow on the phone showed on the PC's Tomorrow.
