# ADR 0007: One SQLite replica schema for both apps, without Room

The spec planned Room on Android and SQLite on Windows for the device replicas (ADR 0002). Both are
SQLite, and both replicate the same server tables with the same outbox and sync state, so two
hand-kept schemas would drift; Tarot2Go already had to mirror its Room migrations in plain SQL by
hand. GoalMaker keeps one replica schema in `replica/migrations/` as immutable
`0001_description.sql` files, tested by the CodePrint SQLite harness (full chain and isolated steps
with fixtures). Android applies the files through `androidx.sqlite` and Windows through
`Microsoft.Data.Sqlite`, each recording applied files and checksums in `schema_migrations`, so a
fresh install and an upgrade take the same path. Android gives up Room's generated DAOs and
compile-time query checks; the replica is a small, table-shaped layer with its own change
notifications, and its behavior is pinned by tests on both platforms.
