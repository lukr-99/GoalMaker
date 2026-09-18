# M1-02: One replica schema for both apps (ADR 0007)

**Status:** done 2026-09-18 · **Milestone:** M1

## Scope
- `replica/migrations/0001_initial.sql`: the six synced tables, `outbox`, `sync_state`.
- CodePrint SQLite harness (`tools/migrations.py`) runs it; `.codeprint.json` lists the folder.
- Both apps bundle the files and apply them with a `schema_migrations` record and checksums.

## Acceptance criteria
- `python tools/migrations.py test replica/migrations` passes; later migrations add fixtures.

## Result
- `replica/migrations/0001_initial.sql` mirrors the six tables plus `outbox` and `sync_state`;
  `tools/migrations.py` tests the chain in CI. Android packages the files as assets through a Gradle
  task, Windows embeds them; both check checksums like the Python tool, and each suite has a test
  that its packaged copy equals the repository file byte for byte.
