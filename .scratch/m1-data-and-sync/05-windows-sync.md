# M1-05: Windows replica and sync

**Status:** done 2026-09-18 · **Milestone:** M1

## Scope
- Replica on `Microsoft.Data.Sqlite` applying the same migration files; same store shape.
- Sync engine per docs/sync.md over PostgREST with the session token; Realtime triggers pulls; a
  5-minute timer; debounced sync after local writes. Sign-out as on Android.

## Acceptance criteria
- Unit tests with a fake remote mirror the Android ones; the contract vectors pass.
- Against the local stack: phone and PC see each other's changes.

## Result
- The same engine in C# over Microsoft.Data.Sqlite and PostgREST, a Realtime feed that also nudges on
  every (re)join, a 5-minute timer, a sync when Windows reports the network back, and the offline
  backoff (15 s doubling to 5 min). Unit tests mirror Android's.
- Against the local stack: pushes, pulls, Realtime, completes, deletes both ways, and an offline add
  that reached the server 13 s after the gateway came back.
