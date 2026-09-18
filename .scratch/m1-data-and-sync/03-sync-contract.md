# M1-03: Sync contract vectors

**Status:** done 2026-09-18 · **Milestone:** M1

## Scope
- `contracts/vectors/sync-merge.json`: merge outcomes (take remote, keep local, apply tombstone,
  drop pending) and the full-resync rule (never synced, older than 80 days).
- Kotlin and C# implement `SyncMerge` and pass every case.

## Result
- `contracts/vectors/sync-merge.json` (merge, full resync, pull start, timestamp form) passes in
  Kotlin and C#. `contracts/schemas/synced-tables.json` describes every column; the
  `check_synced_tables.py` CI step keeps it equal to the replica and, with `--server`, to Postgres.
