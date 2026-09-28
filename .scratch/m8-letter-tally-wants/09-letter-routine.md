# M8-09: The routine that writes the Letter

**Status:** todo · **Milestone:** M8

## Scope
- The canonical routine prompt in `docs/letter.md`, tried against real weeks and tuned for length,
  voice, sections and what it must never change (spec, story 100; ADR 0012).
- The owner's one-time step: a scheduled routine in Claude for Sunday 18:00 with GoalMaker and the
  other apps' connectors on, and optionally a monthly one on the 1st at 07:00. The optional email
  line through Claude's Gmail connector is documented, not built.
- The "weekly summary routine" section of `docs/connector.md` points at the Letter instead.

## Acceptance criteria
- Two real weeks produce letters saved to the right review (the week ending, not the week starting),
  and a second run replaces the first.
- The routine changes nothing but the review's summary (checked in the activity log).

## Vectors to add
- None.

## Check
- Emulator and Windows: the letter from the real routine opens the review on both.
- Endpoint: the routine's calls in the activity log, all by Claude, one write.

## Release
- With M8-07 and M8-08: **1.4.0** (the Letter).
