# M1-02: One replica schema for both apps (ADR 0007)

**Status:** todo · **Milestone:** M1

## Scope
- `replica/migrations/0001_initial.sql`: the six synced tables, `outbox`, `sync_state`.
- CodePrint SQLite harness (`tools/migrations.py`) runs it; `.codeprint.json` lists the folder.
- Both apps bundle the files and apply them with a `schema_migrations` record and checksums.

## Acceptance criteria
- `python tools/migrations.py test replica/migrations` passes; later migrations add fixtures.
