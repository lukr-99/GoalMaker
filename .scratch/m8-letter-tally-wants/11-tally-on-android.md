# M8-11: Tally on Android: usage access and daily totals

**Status:** in progress (the emulator check is left) · **Milestone:** M8

## Scope
- `PACKAGE_USAGE_STATS` in the manifest, unused until the owner turns Tally on (spec, stories 109
  and 111; ADR 0013).
- The permission flow: a card that says what is read and what syncs, a button that opens
  `Settings.ACTION_USAGE_ACCESS_SETTINGS`, the state read again on return, and the card again if
  access is taken away.
- A `UsageSource` port (application) with a `UsageStatsManager` adapter (data) reading foreground
  events since the last run; `TallyRules` turn them into totals, and the existing background sync
  rewrites the touched days. GoalMaker stores nothing raw.

## Acceptance criteria
- Unit tests with a fake `UsageSource`: events into totals, a day across the rollover, the
  watermark, access removed.
- Robolectric for the adapter's event mapping.

## Vectors to add
- None beyond `tally.json`; add cases there if the phone shows an edge the vectors miss.

## Check
- Emulator: grant usage access, use YouTube and Chrome for a few minutes, see the day's totals in the
  replica and on the server; revoke, see the card; reboot.
- Windows: the phone's totals arrive after a sync (visible once M8-13 shows them).
- Endpoint: nothing.

## Result

2026-09-30, Android.

- `PACKAGE_USAGE_STATS` in the manifest, and a `<queries>` entry for the home intent so every
  launcher can be looked up on Android 11 and later.
- `UsageSource` (application) and `UsageStatsSource` (data): resumed opens a stretch and paused
  closes it, per package and activity; screen off, the lock screen and shutdown close every stretch.
  Launchers and the system UI are left out: time on the home screen isn't time in anything.
- `TallyTracker`: while Tally is on and access is granted, reads from the start of the planning day
  the watermark falls in (or up to a week back the first time), sorts with `TallyRules`, and rewrites
  every day up to today with `TallyList.rewrite`. The watermark moves only when every day was written.
  Package names live only in memory.
- It runs before each background sync (every 15 minutes, and the when-online retries), when the app
  comes to the front, when Tally is turned on, and when access is newly granted.
- Until the Tally place exists (M8-13), a Tally section in Settings: the switch, and a card saying
  what is read and what syncs, with a button to the usage access page, shown again if access goes.
  Turning Tally off clears the watermark.
- **Checked:** 390 Android tests (16 new: the tracker with a fake source, the adapter's event mapping
  and the launcher filter under Robolectric, the settings), build and lint.
- **Known:** on the night clocks go back, the repeated hour counts once.
- **Left:** the emulator check: grant access, use YouTube and Chrome, see the day's totals in the
  replica and on the server, revoke, reboot.
