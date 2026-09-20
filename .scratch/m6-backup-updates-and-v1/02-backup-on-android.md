# M6-02: Export and restore on Android

**Status:** done · **Milestone:** M6

## Scope
- Settings, Your data: **Export** writes the M6-01 file wherever the owner picks through the system
  file picker (Storage Access Framework, so no storage permission), named
  `goalmaker-YYYY-MM-DD.json` (spec, story 91).
- **Restore** picks a file, reads it, and shows what it would do (the restore report from M6-01)
  before anything is written. The owner confirms; the restore runs in one replica transaction and
  asks for a sync afterwards, so the restored rows reach the other device.
- A file that is refused says which check failed in plain words, and nothing is written.
- Both work offline: the export reads the replica, not the server.

## Acceptance criteria
- Unit tests over the export and the restore through a test replica, including a refused file and a
  restore into a replica that already holds some of the rows.
- The report the screen shows is the one the rules return, not counted again in the UI.

## Result
- `application/backup/BackupService` reads the replica into a document and applies one back, over
  M6-01's rules: tombstones left out, rows queued so a restore reaches the other devices, and the
  whole file judged before anything is written.
- Settings, Your data: Export writes through the system file picker as `goalmaker-<day>.json` (no
  storage permission), Restore reads a file, shows what it would do, and waits for the owner.
- A file that cannot be written and one that cannot be read say so in their own words, rather than
  borrowing a refusal reason. The first try on a phone showed exactly that muddle.
- Checked on a phone against the local stack: exported 4 tasks, deleted them from the server, wiped
  the replica, restored the file. "Restored: 4 added, 0 updated, 0 left as they were", and all four
  were back on the server after the sync.
