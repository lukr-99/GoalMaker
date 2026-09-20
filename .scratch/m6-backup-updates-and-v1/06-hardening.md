# M6-06: Hardening before the release

**Status:** todo · **Milestone:** M6

## Scope
- The paths that only show up in real use: no network at sign-in and mid-sync, a session that
  expired, a replica file that cannot be opened, a full disk, a clock that jumped, a time zone
  change, and the day rollover crossing while a screen is open.
- Every message the owner can be shown says what happened and what to do next, in the app's voice.
  One pass over the strings of both apps for messages that leaked a technical term. Where those
  messages appear is M6-08.
- Sync under pressure: a large first pull, a device that was off for weeks, two devices editing the
  same row, and the tombstone purge crossing a pull. The merge rule is already pinned by vectors;
  this is about what the apps do around it.
- The connector: a link rotated while Claude is mid-conversation, and the rate limit reached.
- Crash logs: both apps keep one, so check what lands in it and that nothing secret does.

## Acceptance criteria
- A test for each failure path above at the highest seam that can reach it, with fakes for the
  network, the clock and the disk.
- No string in either app's resources names an exception, a table or an HTTP code.
