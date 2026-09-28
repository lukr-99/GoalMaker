# M8-11: Tally on Android: usage access and daily totals

**Status:** todo · **Milestone:** M8

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
