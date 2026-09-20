# M6-03: Export, restore and the weekly backup on Windows

**Status:** done · **Milestone:** M6

## Scope
- Settings, Your data: **Export** and **Restore** as on Android (spec, story 91), through the
  standard save and open dialogs, with the same report and the same refusals.
- The weekly automatic export (story 92): a folder the owner chooses, a file written once a week
  while GoalMaker runs, named `goalmaker-YYYY-MM-DD.json`, keeping the last few and removing older
  ones. It says in Settings when it last wrote and where.
- A week is counted from the last successful export, so a PC that was off does not miss one: the
  next start writes it. A folder that has gone away turns the automatic export off and says so
  instead of failing quietly every week.

## Acceptance criteria
- Tests with a fake clock and a temp folder: the first export, a week later, a PC that was off for
  three weeks, a folder that is gone, and the pruning of old files.
- The same file the Android app writes restores here, and the other way around (M6-01's vectors).

## Result
- `BackupService` and `WeeklyBackup` in Core, `DiskBackupFolder` in Infrastructure, and Settings'
  Your data card with Export, Restore and the weekly folder.
- Checked on the PC against the local stack: exported the four tasks, then restored **the phone's
  own export**, which reported "0 added, 0 updated, 4 left as they were" (M6-01's cross-app
  criterion, and the never-undoes rule, in one go). Pointing the weekly backup at a folder wrote
  `goalmaker-2026-09-20.json` on the next sync and stamped the week.
- Found and fixed a crash while trying it: a window left maximized and started with `--no-activate`
  threw "Cannot show Window when ShowActivated is false and WindowState is set to Maximized" and the
  app died at start-up. `WindowShow.Plan` decides the state to show in and the state to set after,
  with tests for the four cases.
