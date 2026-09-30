# M8-14: Tally through the connector and in the digest

**Status:** in progress (the check with real data on the cloud is left) · **Milestone:** M8

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

## Result

2026-09-30, the connector.

- `get_time_tally(from, to, by)` over `groupTally` in `rules/tally.ts`: the minutes in all, then each
  category, project or device with its share, most first. This week by default. An empty stretch says
  Tally may be off.
- `get_review_digest` gains `tally`: the minutes in all, and by category, project and device, with
  names (the owner's categories by name, defaults by key, projects by name, Phone and PC).
- The routine's prompt in `docs/letter.md` asks where the time went against the plan.
- **Checked:** unit tests for the grouping and the default names (86 Deno tests); the endpoint test
  seeds a fortnight on a PC and a phone plus an own category, groups it all three ways, checks the
  digest's week, and checks that a rule's app name never comes back in any answer (2 tests, 27
  steps, twice).
- **Left:** "How much of this week was coding?" in a Claude chat on the cloud project, once the
  connector is deployed and a device has synced real minutes.
