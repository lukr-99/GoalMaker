# M1-04: Android replica and sync

**Status:** todo · **Milestone:** M1

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
