# M1-04: Android replica and sync

**Status:** done 2026-09-18 · **Milestone:** M1

## Scope
- Replica on `androidx.sqlite` applying `replica/migrations` from assets; table-shaped store with
  change notifications as Flows.
- Sync engine per docs/sync.md over supabase-kt Postgrest; Realtime triggers pulls; WorkManager every
  15 minutes; debounced sync after local writes.
- Sign-out pushes, then clears the replica.

## Acceptance criteria
- Unit tests with a fake remote cover push, pull, merge, overlap, paging, full resync, errors.
- On the emulator against the local stack: a task added on the phone appears in Supabase and a task
  added elsewhere appears on the phone.

## Result
- `SqliteReplica` on the bundled SQLite driver (minSdk 26's framework SQLite has no UPSERT), with a
  statement splitter that follows `sqlite3_complete()`. `SyncEngine`, `SyncCoordinator` (with the
  offline backoff), `PostgrestRemoteTables` over Ktor, a Realtime feed while the app is on screen,
  WorkManager every 15 minutes plus a network-constrained run when changes wait offline.
- 38 new unit tests (Robolectric drives the replica through the framework driver).
- Emulator against the local stack: phone and PC see each other's adds, completions and deletes
  within seconds; an offline add flushes on its own 11 s after the server returns; 1,100 rows
  sharing one `updated_at` page correctly through real PostgREST; the worker runs in the
  background; sign-out warns about an unsynced change, and otherwise pushes then empties the
  replica and cancels background work.
