# Tally

> M8 (`.scratch/m8-letter-tally-wants/`, M8-10 to M8-14): built on both apps and the connector.

Tally shows where the owner's time actually went on the phone and the PC (spec, stories 109 to 113).
The raw record of apps and windows stays on each device; only daily minutes per category (and
project, on the PC) sync ([ADR 0013](adr/0013-tally-keeps-raw-time-on-the-device.md)).

## What syncs

`tally_days`: one row per planning day, device, category and project with its minutes. Its id is a
UUID version 5 of `tally/<owner>/<day>/<device>/<category>/<project or ->`, so a device rewriting a
day replaces its own rows and never another device's. `device` is a random id made once per install;
`device_kind` is `phone` or `pc`. Time is counted on the planning day, so 01:30 before the 04:00
rollover belongs to the day before. Supabase migration 0017, replica migration 0012.

`tally_categories` and `tally_rules` hold the owner's own categories and rules. They sync because they
are the owner's words, not usage.

## Categories and rules

Defaults ship in `contracts/content/tally-rules.json`: Coding, Study, Work, Video, Social, Games,
Reading, Chat and Other, with rules such as `code.exe` → Coding,
`com.google.android.youtube` → Video, and a browser window titled "YouTube" → Video.

A rule matches an **app** (package or executable), a **window title** (contains, case-insensitive)
or a **folder** (from an editor's title), on Android, Windows or both. The owner's rules come before
the defaults and the first match wins; nothing matched is Other. On the PC, an editor's window title
(VS Code, Android Studio, Visual Studio) names its folder, which is matched against projects' local
folders the way `find_project` does, so time in the folder counts toward the project. The phone never
links time to a project, not even through a rule. An editor's title gives the folder's name only,
so a project is found when exactly one project's local folder has that name. Matching, the project
from a title, idle and the daily totals are pinned by `contracts/vectors/tally.json`.

## On each device

Off until the owner turns it on in the Tally place.

- **Android:** a card explains what is read and what syncs, and opens the system's Usage access page
  on GoalMaker's own row. GoalMaker keeps no raw data: before each background sync (every 15
  minutes), when the app comes to the front and when access is granted, it reads `UsageStatsManager`
  events from the start of the last day it read (Android keeps about a week) and rewrites those days'
  totals. One app is in front at a time; the home screen and the system UI don't count. It needs
  `QUERY_ALL_PACKAGES`, since Android 11 only shows the usage of apps this one can see. If access is
  taken away, the card comes back.
- **Windows:** one switch. The tray app follows the foreground window with `SetWinEventHook`, reads
  the title every 15 seconds (browser tabs change without a window switch), and stops the clock after
  5 minutes without input (`GetLastInputInfo`), on lock and on sleep. A window in the Video category
  is not idle without input. The raw log stays in `%LOCALAPPDATA%\GoalMaker\tally\` for 30 days.

## Where it shows

- **The Tally place:** the switch (and on the phone the usage access card) at the top, filter chips
  (Phone, PC, and each category with time this week), today as one stacked bar by category, the week
  as stacked bars per day, time per project this week, and the owner's own rules and categories to
  add, edit and delete. It is a place like any other: in the Places hub and the sidebar, pinnable.
  Bars grow in over the standard duration, or fade in with reduced motion.
- **Stats:** "Where the time went", twelve weeks of stacked columns by category (`weeks` in
  `contracts/vectors/tally.json`), shown once there is Tally time.
- **The review:** the period's time by category in the look-back, shown once there is Tally time.
- **The connector:** `get_time_tally(from, to, by = category | project | device)`, this week by
  default, and a `tally` section in `get_review_digest` (the minutes in all, and by category, project
  and device). A default category is named by its key (coding is Coding), since the deployed function
  doesn't read the shipped file; a test keeps the two equal. Comparing time with what was planned is
  left to Claude and the [Letter](letter.md).
