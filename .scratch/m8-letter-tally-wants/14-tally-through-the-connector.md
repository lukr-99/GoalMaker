# M8-14: Tally through the connector and in the digest

**Status:** todo · **Milestone:** M8

## Scope
- `get_time_tally(from, to, by = category | project | device)` over `rules/tally.ts` (spec, story
  113), and a `tally` section in `get_review_digest` (M8-07), so the Letter can say how much of the
  week was coding.
- `docs/connector.md` and `docs/letter.md` gain it, and the routine prompt mentions time.

## Acceptance criteria
- Endpoint tests: a seeded fortnight grouped all three ways; the digest carries it; no app or window
  name appears in any answer.

## Vectors to add
- None beyond `tally.json`.

## Check
- Emulator and Windows: the totals Claude reports match the Tally place.
- Endpoint: "How much of this week was coding?" in a Claude chat on the cloud project.

## Release
- With M8-10 to M8-13: **1.5.0** (Tally).
