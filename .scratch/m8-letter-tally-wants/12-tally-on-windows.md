# M8-12: Tally on Windows: the foreground tracker

**Status:** in progress (the desktop check is left) · **Milestone:** M8

## Scope
- In the tray app, off until switched on (spec, stories 109 to 111; ADR 0013):
  `SetWinEventHook(EVENT_SYSTEM_FOREGROUND)` for switches, the title read every 15 seconds, idle
  after 5 minutes without input (`GetLastInputInfo`), lock and sleep from the session and power
  events, and Video windows exempt from idle.
- The executable from the window's process; the folder from an editor's title (VS Code, Android
  Studio, Visual Studio) for the project.
- A raw log in `%LOCALAPPDATA%\GoalMaker\tally\` (`GoalMaker-dev` for dev builds), one file a day,
  kept 30 days; totals rewritten for the touched days on the sync timer.
- An `IForegroundSource` port (Core) with the Win32 adapter (Infrastructure), so the rules are
  tested without a desktop.

## Acceptance criteria
- Core tests with a fake source and clock: switches, titles, idle, lock, the Video exception, the
  project from a title, the 30-day cleanup.
- The tracker costs nothing measurable at rest (nothing polls faster than every 15 seconds).

## Vectors to add
- None beyond `tally.json`.

## Check
- Emulator: nothing.
- Windows: switch it on, work in VS Code in this repository, watch a video, walk away for 6 minutes,
  lock; check the day's totals and the GoalMaker project's minutes in the replica and on the server.
- Endpoint: nothing.

## Result

2026-09-30, Windows.

- `IForegroundSource` (Core) and `WindowsForegroundSource` (Infrastructure):
  `SetWinEventHook(EVENT_SYSTEM_FOREGROUND)`, the executable through the window's process, the title
  on each look, `GetLastInputInfo`, and lock and sleep from the session and power events.
- `TallyTracker` (Core, with a `TimeProvider`): a switch, a lock or sleep looks at once, and a timer
  looks every 15 seconds; nothing polls faster. A new app or title ends a stretch; five minutes
  without input ends it at the last input plus five minutes, unless it is Video; lock and sleep end it
  at once, and a tick more than two minutes late counts as sleep.
- The raw log (`ITallyLog`, `DiskTallyLog`): one `yyyy-MM-dd.jsonl` a day in
  `%LOCALAPPDATA%\GoalMaker\tally\` (`GoalMaker-dev` for dev builds), each line with the category
  and project as sorted then, kept 30 days. It never syncs and is not in backups.
- On the five-minute sync timer, at switch-off and at shutdown, `Flush` writes the open stretch,
  removes old files, and rewrites each touched day with `TallyRules.DayTotals` and
  `TallyList.RewriteDay`, reading the neighbor days' files so the 04:00 cut comes out right.
- Until the Tally place exists (M8-13), a Tally card in Settings with the switch and what is recorded
  and what syncs.
- `GoalMaker.Infrastructure` now references the Windows desktop framework for `SystemEvents`, which
  also carries `ProtectedData`, so that package reference went.
- **Checked:** 508 Windows tests (13 new: switches, titles, idle, lock and sleep, Video, a late tick,
  the project from a VS Code title, the 30-day cleanup, the rewrite across 04:00, a day waiting for
  sign-in, one look every 15 seconds, the disk log), `dotnet format` and the accessibility scan.
- **Known:** UWP apps show as `ApplicationFrameHost.exe` and elevated windows as Other; a stretch
  across the autumn clock change can be dropped.
- **Left:** the desktop check: VS Code in this repository counting toward GoalMaker, a video, six
  minutes away, a lock, and the totals in the replica and on the server.
