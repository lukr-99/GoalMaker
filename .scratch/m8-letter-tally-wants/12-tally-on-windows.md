# M8-12: Tally on Windows: the foreground tracker

**Status:** todo · **Milestone:** M8

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
