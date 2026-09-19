# M3-08: Activity log with undo in both apps

**Status:** todo · **Milestone:** M3

## Scope
- An Activity screen on both apps: recent changes newest first, each with what changed in plain
  words, when, and "by Claude" for the connector's (spec, story 72), with Undo on each entry that
  can still be undone (story 19).
- Read online from `activity_log` (it isn't part of the replica); undo calls `undo_activity`,
  and the restored row reaches the replica through normal sync.

## Acceptance criteria
- A change made through the connector appears as "by Claude" and Undo puts it back on both apps.
- Offline, the screen says it needs a connection instead of showing an empty log.
