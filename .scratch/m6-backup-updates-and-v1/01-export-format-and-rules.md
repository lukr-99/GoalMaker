# M6-01: The export format and the restore rules

**Status:** todo · **Milestone:** M6

## Scope
- A versioned JSON export of everything the owner has (spec, story 91): the profile's settings that
  belong to the data (time zone, day start), and every synced table in dependency order, with
  tombstones left out and ids kept as they are.
- The format pinned by `contracts/vectors/backup.json` and run by Kotlin and C#: the file's shape
  (`version`, `exportedAt`, `owner`, `tables`), which tables it carries and in which order, and what
  an older `version` means for a reader.
- The restore rules in the same vectors: what a restore checks before it changes anything (the
  format version it can read, that the file belongs to this owner, that every row names a table it
  knows and every reference inside the file resolves), what it does with rows that are already there
  (last writer wins by `updated_at`, the same rule sync uses), and what it never does (it does not
  delete rows the file lacks, so a restore is a merge and never a silent wipe).
- A restore report: how many rows each table gained, kept and updated, so both apps can show the
  owner what happened before and after.

## Acceptance criteria
- Every vector case passes in Kotlin and C#: a file written by one app reads in the other, byte for
  byte the same export from the same replica.
- A file with a newer format version, a stranger's owner id, an unknown table or a broken reference
  is refused whole, with nothing written.
- Round trip: export, wipe a replica, restore, and the replica matches row for row.
