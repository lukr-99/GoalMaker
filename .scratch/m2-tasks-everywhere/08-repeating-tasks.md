# M2-08: Repeating tasks

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- Presets (daily, weekdays, weekly on chosen days, every N days, monthly on a date) stored as an
  RRULE subset; the next occurrence appears when the current one is done or skipped.
- `contracts/vectors/recurrence.json` for expansion, run by both apps.
- Plan tomorrow's Done and Drop go through the same path, so deciding on a repeating task in the
  ritual moves the series on too (docs/plan-tomorrow.md: Later).

## Acceptance criteria
- A series never shows two open occurrences, on either device, after offline edits on both.

## Result
- Rules in `docs/repeating.md`, pinned by `contracts/vectors/recurrence.json`: 32 next-day cases
  (every preset, intervals, late and early finishing, short months, invalid rules), successor and
  tag-link ids (UUID v5, cross-checked against Python's uuid5), and 7 repair cases. Both apps pass.
- Each occurrence is its own row in a series (`series_id`). Done or Drop makes the next one with
  the same plan (title, notes, time, area, tags, top priority, repeat) on the first matching day
  after the later of its day and today; reopening (undo, Plan tomorrow) takes it back.
- Two devices: the next occurrence's id derives from the current one's, so both make the same row;
  after a sync that pulled rows, a series with two open occurrences keeps the one planned latest and
  drops the others, identically on every device.
- Verified: two-device tests on both apps (same occurrence finished on both; a device offline for
  two days pushing a stale occurrence) end with one open occurrence on both devices and the server.
  By hand on the emulator: finishing a daily task put the next on Tomorrow, which showed on the PC;
  undo on the next one left the server with the right rows (done, reopened, taken back).
- Open: steps (M2-12) aren't copied yet; a finished occurrence reopened by a stale sync comes back
  dropped rather than done (documented).
