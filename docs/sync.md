# Sync

How a device's replica and Supabase stay in step (ADR 0002, ADR 0007). Both apps implement exactly
this; the decisions marked **contract** are pinned by `contracts/vectors/sync-merge.json`.

## Synced tables

`areas`, `tags`, `tasks`, `task_steps`, `task_tags`, `reminders` (M1). Later milestones add goals,
habits, projects and reviews the same way. Every synced table has:

| Column | Set by | Meaning |
| --- | --- | --- |
| `id` | device | UUID, made when the row is created, so offline creation needs no server |
| `owner_id` | server | `auth.uid()`; row security allows only the owner |
| `created_at` | device | when the owner created it |
| `updated_at` | server | stamped by a trigger on every write; the sync clock |
| `deleted_at` | device | tombstone; `null` while the row is alive |

Rows are never hard-deleted by a device. A scheduled job purges tombstones older than 90 days.

## Local writes

A change is written to the local table and, in the same transaction, appended to the `outbox` as the
full row. The UI reads only the local tables, so it never waits for the network. The row keeps the
`updated_at` it last had from the server until the push returns a new one.

## Push

1. Take outbox entries in order (`seq`).
2. Upsert each row by `id` through PostgREST (`on_conflict=id`, merge duplicates), returning the
   stored row.
3. Write the returned row (with the server's `updated_at`) into the local table and delete the outbox
   entry, in one local transaction.
4. On a network error stop and retry later with backoff; on a rejected row (row security, a check
   constraint) record the error on the entry, skip it, and surface it in the app.

Pushing is idempotent: repeating an upsert stores the same row again.

## Pull

For each table, fetch rows with `updated_at > watermark - 60 s`, ordered by `(updated_at, id)`, in
pages of 500, including tombstones. The 60-second overlap covers writes that committed out of order
around the previous pull; re-fetched rows merge as no-ops. The watermark becomes the largest
`updated_at` seen.

## Merge (contract)

For each pulled row, with the local copy (if any) and whether the outbox holds a change for it:

| Remote | Local change pending | Result |
| --- | --- | --- |
| alive | no | take the remote row if it is newer than the local one (by `updated_at`), otherwise keep local |
| alive | yes | keep the local row; the pending push will win on arrival |
| tombstone | no | take the tombstone |
| tombstone | yes | **delete wins**: take the tombstone and drop the pending change |

"Newer" compares server timestamps only; device clocks never decide anything.

## Full resync (contract)

A device resyncs from scratch when it has never synced a table, or when its watermark is older than
**80 days** (10 days inside the 90-day tombstone purge, so it can't miss a purged deletion). A full
resync first pushes the outbox, then replaces the local rows of that table with the server's.

## When sync runs

- At start-up and after sign-in; after local writes (debounced by 2 seconds).
- When Supabase Realtime reports a change to a synced table (the event only triggers a pull; its
  payload is not applied directly).
- Android: every 15 minutes in the background (WorkManager). Windows: every 5 minutes while running.
- When the Realtime channel (re)joins, because events sent while disconnected are lost.
- One sync at a time per device; a request during a sync schedules one more run after it.
- While the server can't be reached, the device retries on its own: after 15 seconds, then doubling
  up to every 5 minutes. A local write or the OS reporting the network back retries sooner. Windows
  listens for network changes; Android relies on WorkManager's network constraint.

## Sign-out

The app pushes the outbox once more, then clears every synced table, the outbox and the watermarks,
so the next account starts clean. If the push fails, it asks before discarding pending changes.
