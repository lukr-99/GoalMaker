# M3-08: Activity log with undo in both apps

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- An Activity screen on both apps: recent changes newest first, each with what changed in plain
  words, when, and "by Claude" for the connector's (spec, story 72), with Undo on each entry that
  can still be undone (story 19).
- Read online from `activity_log` (it isn't part of the replica); undo calls `undo_activity`,
  and the restored row reaches the replica through normal sync.

## Acceptance criteria
- A change made through the connector appears as "by Claude" and Undo puts it back on both apps.
- Offline, the screen says it needs a connection instead of showing an empty log.

## Result
- `contracts/vectors/activity.json` pins what an entry did (added, completed, moved, archived,
  handled and so on); `ActivityRules` in Kotlin and C# pass it. docs/activity.md.
- Android: Settings, Claude, Activity; Windows: Activity in the sidebar (and `--open activity`).
  Sentences like "Claude moved “Call the bank” to Mon 21 Sep", Undo on each row's latest change,
  and what an undo came to (done, changed since, already undone). A successful undo asks for a sync.
- Checked on the local stack: undoing Claude's added task on the phone deleted it softly on the
  server and it left the PC's Today; the phone's list then said "You deleted".
