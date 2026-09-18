# Device replica schema

The SQLite schema both apps keep locally (ADR 0007, [docs/sync.md](../docs/sync.md)). The files in
`migrations/` are immutable and follow CodePrint's naming; the Android app bundles them as assets and
the Windows app embeds them, and both record applied files with their SHA-256 in
`schema_migrations`, exactly like `tools/migrations.py`.

```powershell
python tools/migrations.py test replica/migrations
python tools/migrations.py test replica/migrations --migration 0002 --fixture replica/fixtures/0002_existing.sql
```

A new migration needs a fixture of representative existing data for its isolated test, and a
matching change on the server (`supabase/migrations/`) when it mirrors server columns.
