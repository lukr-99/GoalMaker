# M3-01: Connector links, owner context and undo in the database

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- Migration 0007: `connector_links` (hashed secret, created at, last used at, revoked at, a
  per-minute call window for rate limiting), with row security for the owner.
- `create_connector_link()` makes a random secret, stores only its SHA-256 hash, revokes the
  previous link (so creating is also rotating) and returns the secret once;
  `revoke_connector_links()` kills every active link (spec, story 77; ADR 0003).
- `connector_resolve(hash)` for the Edge Function only: the owner of an active link, with the call
  counted and refused over the limit. Not callable by `anon` or `authenticated`.
- The activity log gets `undone_at` and the `system` actor; `undo_activity(id)` puts the row back
  the way the log entry found it (a create is undone by a soft delete), as the owner, and marks the
  entry undone (spec, stories 19 and 72).

## Acceptance criteria
- pgTAP: the owner can create, rotate, list and revoke links; a stranger and anon can't see them;
  only the hash is stored; resolve refuses revoked links and the 121st call in a minute.
- pgTAP: undo restores an update, a delete and a create, refuses someone else's entry and an entry
  already undone, and the undo itself is logged.
- The migration harness passes the full chain and the isolated 0006 to 0007 step.

## Result
- `supabase/migrations/0007_connector_links_and_undo.sql`, locked. Secrets are 43 URL-safe
  characters (32 random bytes); owners can read when a link was made, used and revoked, not its
  hash. `connector_resolve` is granted to `service_role` only and allows 120 calls a minute.
- `undo_activity` refuses with `40001` when the row changed after the entry, so an undo never
  throws away later work; `55000` when already undone, `P0002` for someone else's entry.
- pgTAP: 25 checks in `supabase/tests/database/0007_connector_links_and_undo.test.sql`; fixtures
  `supabase/migration-tests/0007_*.sql` undo an entry logged before 0007 and resolve a new link.
