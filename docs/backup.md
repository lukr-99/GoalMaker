# Backup and restore

An export is one JSON file holding everything the owner has (spec, story 91). Both apps write the
same file and read each other's, which
[`contracts/vectors/backup.json`](../contracts/vectors/backup.json) pins, down to the bytes.

## The file

```json
{
  "format": "goalmaker.backup",
  "version": 1,
  "exportedAt": "2026-09-20T19:52:08.500000Z",
  "app": "windows",
  "appVersion": "1.0.0",
  "owner": "11111111-1111-4111-8111-111111111111",
  "tables": { "areas": [ ... ], "tasks": [ ... ] }
}
```

The tables are the synced tables in their own order, so a row is written after whatever it points
at, and each row is the row the replica holds. **Tombstones are left out**, so a row may point at
something the file does not carry (a task whose area was deleted); that is not an error.

The text is indented with two spaces, keys keep the order above, and anything outside ASCII is
written as itself, so the same replica exports to the same bytes on either app.

## What a restore checks

The whole file is judged before anything is written, and one problem means nothing was:

| Refused | What it means |
|---|---|
| Not a backup | Another kind of JSON, or not JSON at all |
| Too new | Written by a newer GoalMaker, which this one would have to guess at |
| Another owner | Somebody else's export, or a row in it belongs to somebody else |
| Unknown table | It carries a table this build knows nothing about |
| Row without id | A row with no id, so there is no saying what it is |

## What a restore does

A restore **merges and never wipes**: it never deletes a row the file lacks, so restoring an old file
onto a live replica adds what is missing and leaves the rest. Row by row, the file's row is taken
when there is none here yet, or when the file's `updated_at` is newer than the local one. That is the
rule sync already uses, so a restore cannot undo newer work.

The report says how many rows were added, updated and kept, and both apps show it before the owner
confirms and after it runs.

## In the apps

Android: Settings, Your data. **Export** writes through the system file picker, so no storage
permission is asked for, and offers the name `goalmaker-<day>.json`. **Restore** picks a file, says
what it would do, and waits for the owner to agree. A file that cannot be written or read says so on
its own terms; a file that is refused says which check failed.
