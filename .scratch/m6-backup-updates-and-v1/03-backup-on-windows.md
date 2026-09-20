# M6-03: Export, restore and the weekly backup on Windows

**Status:** todo · **Milestone:** M6

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
