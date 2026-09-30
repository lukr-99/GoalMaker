# M6-06: Hardening before the release

**Status:** done · **Milestone:** M6

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

## Progress

2026-09-21. The string pass found one leak and it is gone: a sign-in that failed for any other reason
showed the server's own words, in both apps, and now says "That did not work. Try again in a moment."
Nothing else in either app's strings names an exception, a code or a table; the dev-only backend hint
names a localhost address, which is what it is for.

A replica that will not open used to end the Windows app before a window appeared, which is what a
migration this build did not know did earlier today. It now says so, names the folder the data is in,
and writes the exception to logs/crash.log.

Left: the rest of the real-use paths (no network mid-sync, an expired session, a full disk, a clock
jump, a time zone change, the day rolling over while a screen is open), the same startup failure on
Android, and sync under pressure.

### Checked, already held

- **The day rolling over while a screen is open, a clock jump, a time zone change.** Android rebuilds
  its lists from `clock()` every minute; Windows has the day timer and listens for the system's time
  and power events. Both pick a jump up on the next tick.
- **A large first pull.** The engine pages at 500 with a cursor, and `SyncEngineTests` pulls 1,017
  rows across three pages to prove it.
- **No network mid-sync.** The run reports offline, the outbox keeps what it has, and the status says
  so without a word from the server.

### Left, and they want hands rather than a scan

A full disk, a session the server refuses while the app is open, keyboard reach on Windows, and both
apps at the largest system text size. Worth doing on the real devices during M6-07, not guessed at
here.

## Close-out (2026-09-28)

Two paths are left for v1: Android shows the "replica won't open" screen Windows already has
instead of closing, and a session the server refuses while an app is open sends the owner to
sign-in on both apps without losing the outbox. A full disk and sync under pressure move to
"After v1" in the roadmap.

## Result (2026-09-30)

Both paths left at the close-out are built.

- **A replica that won't open, on Android.** `AppGraph` opens the replica once at start, off the main
  thread (`ReplicaStartup`); a failure goes to `files/crash.log` and becomes a state, and `ReplicaGate`
  shows the startup failure screen instead of the app, quick add or the share sheet. A migration this
  build doesn't know throws `ReplicaFromNewerAppException`, and the screen says the data was saved
  by a newer version and to install the newest one; anything else says to ask for help before
  reinstalling, since a reinstall would lose unsynced changes. Reminders and background sync do
  nothing then. Checked on the emulator: a replica from a newer build shows the screen, and the line
  is in crash.log.
- **A session the server refuses, on both apps.** A 401 used to count as offline forever. Now a 401
  renews the session once and tries again; a second 401, or a renewal the server refuses, ends the
  session. The app goes to sign-in with one line saying the session ended, the replica and outbox
  stay, and the coordinator stops retrying while nobody is signed in. Signing in again as the same
  owner syncs what waited; signing in as someone else empties the replica first (`AccountSync` on
  Android, `SessionSync` on Windows), and the engine only pushes the signed-in owner's rows. Windows
  also fixed a sign-out made offline that never cleared the client's stored session, so a weekly
  sign-out done offline could quietly sign back in.
- **Checked:** 367 Android tests with build and lint, 489 Windows tests (251 Core, 238 App) with
  `dotnet format`, and the accessibility scan. New tests on both apps: a refused session signs out
  and keeps the outbox without retrying, a 401 after a renewal ends it, a renewal that can't reach the
  server keeps the session, the same owner syncs, another owner gets nothing.
- **Left, by hand against the local stack:** delete the signed-in user while an app is open with a
  change waiting, and watch the line appear and the change arrive after signing in again.
