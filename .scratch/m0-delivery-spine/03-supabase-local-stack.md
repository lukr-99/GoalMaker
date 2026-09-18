# M0-03: Supabase local stack, first migrations, tests

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- `supabase/config.toml` for the local stack; email sign-in with a 6-digit code; local email
  template with the code.
- `0001_profiles.sql`: `profiles` with owner row security, created on sign-up.
- `0002_release_channel.sql`: private `releases` Storage bucket, read for signed-in users only.
- Migration harness: full chain from `0001` and isolated N-1 → N with fixtures.
- pgTAP tests for row security and the bucket policy.

## Acceptance criteria
- `tools/supabase-test.ps1` (and the CI job) starts the stack, runs the harness and pgTAP, and
  fails on any error.

## Result
- The harness is `tools/supabase_migrations.py` (lock, verify, test) rather than a PowerShell script. Migrations are locked by checksum in `supabase/migrations.lock.json`; fixtures live in `supabase/migration-tests/`.
- The local stack uses ports 553xx because another project's stack holds 543xx.
- Verified: a failing migration rolls back completely under the CLI.
