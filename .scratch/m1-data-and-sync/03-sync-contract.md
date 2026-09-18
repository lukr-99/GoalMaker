# M1-03: Sync contract vectors

**Status:** todo · **Milestone:** M1

## Scope
- `contracts/vectors/sync-merge.json`: merge outcomes (take remote, keep local, apply tombstone,
  drop pending) and the full-resync rule (never synced, older than 80 days).
- Kotlin and C# implement `SyncMerge` and pass every case.
