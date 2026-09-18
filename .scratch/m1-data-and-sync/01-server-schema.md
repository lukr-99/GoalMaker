# M1-01: Server schema for the planning core

**Status:** done 2026-09-18 · **Milestone:** M1

## Scope
- `0003_planning_core.sql`: `areas`, `tags`, `tasks`, `task_steps`, `task_tags`, `reminders` with the
  sync columns from docs/sync.md, owner-only row security (no client deletes), a trigger that stamps
  `updated_at` with the server clock and keeps `owner_id`, `(owner_id, updated_at)` indexes, and the
  tables in the Realtime publication.
- `0004_activity_log.sql`: a server-written log of every change (actor from the
  `x-goalmaker-actor` request header, default `owner`), readable by its owner.
- `0005_tombstone_purge.sql`: `purge_tombstones()` plus a daily pg_cron job; 90 days.

## Acceptance criteria
- Harness passes: full chain, isolated steps with fixtures, pgTAP.
- pgTAP proves: owner-only read and write on every table, stranger and anonymous get nothing, clients
  can't hard-delete, `updated_at` is the server's, the purge removes only old tombstones.

## Result
- Migrations 0003 (areas, tags, tasks, task steps, task tags, reminders with owner-only row
  security, server-stamped `updated_at`, same-owner composite keys, the Realtime publication), 0004
  (activity log with the `claude` actor header) and 0005 (the 90-day tombstone purge on pg_cron),
  each with pgTAP tests and an isolated-migration fixture.
