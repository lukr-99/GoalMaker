# M8-10: Tally: data, categories and the sorting rules

**Status:** done · **Milestone:** M8

## Scope
- Supabase migration 0017 and replica migration 0012 (ADR 0013; [tally](../../docs/tally.md)):
  `tally_days` (day, device, device kind, category, project, minutes 0 to 1,440, a v5 id from those),
  `tally_categories` (name, palette color, emoji, position) and `tally_rules` (match `app | title |
  folder`, pattern, platform `android | windows | any`, category, project, position), all synced
  with row security and tombstones. Categories and rules go through the activity log with undo;
  Tally days don't, since a device rewrites them.
- `contracts/content/tally-rules.json`: the default categories and rules, checked by a new
  `tools/check_tally_rules.py` in the repository validation.
- `TallyRules` in Kotlin, C# and `rules/tally.ts`: match an app, a title or a folder (the owner's
  rules first, first match wins, Other otherwise), the project from an editor's window title against
  projects' folders, idle, and a device's intervals turned into planning-day totals.
- `TallyList` in both apps: rewrite a day's rows for this device, read totals.

## Acceptance criteria
- pgTAP for the three tables; the harness in full and as 0016 → 0017; synced tables checked.
- Every `tally.json` case passes in Kotlin, C# and TypeScript.
- No column anywhere can hold an app or window name except `tally_rules.pattern`.

## Vectors to add
- `contracts/vectors/tally.json` (new, listed in `contracts/README.md`): `match` (app, title,
  folder, an owner rule before a default, platform, case), `project` (VS Code, Android Studio and
  Visual Studio titles, a folder inside a project's, no match), `idle` (5 minutes, lock, sleep, the
  Video exception), `days` (intervals across midnight and the 04:00 rollover, overlapping
  intervals, the ids).
- `contracts/vectors/backup.json`: the table order with the Tally tables.

## Check
- Emulator and Windows: the replicas migrate; nothing visible yet.
- Endpoint: nothing yet.

## Result

2026-09-30, the server, the shared rules and both apps.

- **Supabase 0017 and replica 0012:** `tally_days`, `tally_rules` and `tally_categories`, synced with
  row security and tombstones. The server refuses a phone row with a project, a title or folder rule
  on Android, and more than 1,440 minutes. Only rules and categories go through the activity log.
  pgTAP (21 tests, one of them that a tally day's columns can't hold an app or a window), the full
  and isolated 0016 to 0017 runs, the replica chain and the synced-tables check against the server
  all pass. `backup.json` carries the three tables; both apps' backups follow the catalog.
- **`contracts/content/tally-rules.json`:** 9 categories and 48 rules, checked by the new
  `tools/check_tally_rules.py` in CI. Editors come before the title rules, so `youtube.ts` in VS Code
  is still Coding.
- **`contracts/vectors/tally.json`** (50 cases) and the same rules in `rules/tally.ts`, Kotlin and
  C#: matching, editor folders (VS Code, Android Studio, Visual Studio), projects, idle, daily totals
  with the rollover and overlaps, and the ids. The ports found three gaps in the first version, now
  pinned: a phone sample never gets a project (not even from a rule), a folder rule never matches
  without a folder, and the activity log names a category by its name and a rule by its pattern
  (`activity.json`).
- **Changed from the scope:** an editor's title only gives the folder's name, not its path, so "a
  folder inside a project's" can't be told from a title; a project is found when exactly one
  project's folder has that name, and two with the same name give none.
- **`TallyList` on both apps:** rewrites this device's rows for a day (only what changed), reads
  totals, and keeps the owner's categories and rules; each install makes its device id once.
- **Checked:** 374 Android tests with build and lint, 495 Windows tests and `dotnet format`, 50 Deno
  cases, pgTAP, the migration harness and repository validation. Nothing is visible in the apps yet.
