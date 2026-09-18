# M1-05: Windows replica and sync

**Status:** todo · **Milestone:** M1

## Scope
- Replica on `Microsoft.Data.Sqlite` applying the same migration files; same store shape.
- Sync engine per docs/sync.md over PostgREST with the session token; Realtime triggers pulls; a
  5-minute timer; debounced sync after local writes. Sign-out as on Android.

## Acceptance criteria
- Unit tests with a fake remote mirror the Android ones; the contract vectors pass.
- Against the local stack: phone and PC see each other's changes.
