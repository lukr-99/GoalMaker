# M8-09: The routine that writes the Letter

**Status:** in progress (real weeks on the cloud are left) · **Milestone:** M8

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

## Result

2026-09-30, the docs and a dry run.

- **The prompt** in `docs/letter.md` now leaves out a section the week gives nothing for, keeps a
  quiet week to a few honest sentences, never invents, and says the save is the only change. The dry
  run on a nearly empty week showed why: without that, the prompt asks for five sections and a quiet
  week has nothing for most of them.
- **Dry run on the local stack**, as the routine: `get_review_digest` with no period on a Wednesday
  gave the week holding yesterday; `save_review_summary` with its period start wrote one review, and
  the activity log shows one change by Claude. The same letter again changed nothing; a changed one
  updated the same review, by Claude, so a second run replaces the first.
- `docs/connector.md`'s weekly summary routine now points at the Letter, and `docs/letter.md` has
  what a run does and the owner's setup steps. The email line stays documented, not built.
- **Left:** two real weeks through the cloud connector, which needs M8-07 merged and the connector
  deployed, and the owner's scheduled routine in Claude. Then the letter from the real routine
  opening the review on both apps.
