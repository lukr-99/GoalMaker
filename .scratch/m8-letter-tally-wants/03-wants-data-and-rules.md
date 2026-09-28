# M8-03: Wants: data and the cooldown rules

**Status:** todo · **Milestone:** M8

## Scope
- Supabase migration 0016: `wants` (title, reason, link, price, currency, area, cooldown days, cools
  until, decision `bought | dropped`, decided at, decision note, last checked price with when and a
  note, made by) with the usual synced columns, row security, the activity log trigger, undo, the
  purge, grants and the Realtime publication; and `profiles.want_cooldowns` (the thresholds, the
  no-price days and the owner's currency) with a check on its shape ([wants](../../docs/wants.md)).
- Replica migration 0011 and the `wants` entry in `contracts/schemas/synced-tables.json`; the backup
  carries it in the synced-tables order.
- `WantRules` in Kotlin, C# and `rules/wants.ts`: the cooldown from a price, the state (cooling,
  ready, decided) on a planning day, the day the ready notification names a want, and the stats
  block (bought against dropped, money not spent).
- `WantList` in both apps over the replica, beside `TaskList`.

## Acceptance criteria
- pgTAP: owner can, stranger can't, anonymous can't; the checks (reason required, decision and
  decided at together, a price not negative, a three-letter currency). The migration harness passes
  in full and as 0015 → 0016 with fixtures; `tools/check_synced_tables.py` passes.
- Every `wants.json` case passes in Kotlin, C# and TypeScript.
- A backup round trip keeps wants.

## Vectors to add
- `contracts/vectors/wants.json` (new, listed in `contracts/README.md`): `cooldown` (each threshold,
  exactly 1,000, no price, another currency, a picked number of days), `state` (the day before, on
  and after `cools_until`, across the 04:00 rollover, decided, reopened), `ready` (which wants a
  day's notification lists, quiet hours), `stats` (bought, dropped, money not spent, deleted wants
  left out).
- `contracts/vectors/backup.json`: the table order with `wants`.

## Check
- Emulator and Windows: nothing visible yet; both apps' replicas migrate from 0010 with data.
- Endpoint: nothing yet (the tools are M8-06).
