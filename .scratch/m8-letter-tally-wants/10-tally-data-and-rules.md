# M8-10: Tally: data, categories and the sorting rules

**Status:** todo · **Milestone:** M8

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
