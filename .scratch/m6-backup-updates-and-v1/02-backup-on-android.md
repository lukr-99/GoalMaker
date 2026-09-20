# M6-02: Export and restore on Android

**Status:** todo · **Milestone:** M6

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
