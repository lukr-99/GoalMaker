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

Defaults ship in `contracts/content/tally-rules.json`: Coding, Study, Work, Productivity (planning,
notes, to-dos and calendars, GoalMaker itself included), Email, Video, Music, Social, Chat, Games,
Reading, News, Design, Shopping, Finance, Fitness, Navigation and Other, each in its own palette
color, with rules such as `code.exe` → Coding, `com.spotify.music` → Music,
`com.google.android.youtube` → Video, and a browser window titled "YouTube" → Video. A default's key
never changes once shipped, since synced days name it, and its name is the key capitalized;
`tools/check_tally_rules.py` holds the file to that. The app rules come before the title rules, so an
app's own rule wins over a word in its window title (a Word document called "YouTube plan" is Work),
and a narrower title comes before a wider one ("YouTube Music" before "YouTube"). In October 2026 the
nine newer categories joined and Gmail and Outlook moved from Work to Email. Days already counted
keep their category; only time counted afterwards (and today, counted again) sorts the new way.

A rule matches an **app** (package or executable), a **window title** (contains, case-insensitive)
or a **folder** (from an editor's title), on Android, Windows or both. The owner's rules come before
the defaults and the first match wins; nothing matched is Other. On the PC, an editor's window title
(VS Code, Android Studio, Visual Studio) names its folder, which is matched against projects' local
folders the way `find_project` does, so time in the folder counts toward the project. The phone never
links time to a project, not even through a rule. An editor's title gives the folder's name only,
so a project is found when exactly one project's local folder has that name. Matching, the project
from a title, idle and the daily totals are pinned by `contracts/vectors/tally.json`.

## Sorting

What landed in **Other** is listed at the top of the place's apps as **To sort**, most minutes first:
the apps (and on the PC the sites and editor folders) of this device's own record, for the day shown
or the week. Each has the categories one tap away. A tap saves a rule and counts today again, so the
item leaves the list. Every app, site and folder in the apps also has **Move to**, the same thing
from any category, beside **Make a rule**, which opens the full rule sheet.
A browser or editor that lands in Other is listed by its sites or folders, never as the app: an app
rule for a browser would come before the shipped site rules and pull every site with it. The same
site in two browsers is one line.

Sorting the same thing again changes the owner's rule for exactly that match, pattern (ignoring
case) and platform, rather than adding a second one: an app is an app rule, a site a title rule and
a folder a folder rule, for the device it is on. One of the owner's own categories can be **merged**
into another, after a question: its rules sort into the other one from then on, and it is deleted.
Days already counted keep the category they were counted in (the owner's pick, 2026-10-05).

## On each device

Off until the owner turns it on in the Tally place.

- **Android:** a card explains what is read and what syncs, and opens the system's Usage access page
  on GoalMaker's own row. GoalMaker keeps no raw data: before each background sync (every 15
  minutes), when the app comes to the front and when access is granted, it reads `UsageStatsManager`
  events from the start of the last day it read (Android keeps about a week) and rewrites those days'
  totals. One app is in front at a time; the home screen and the system UI don't count. It needs
  `QUERY_ALL_PACKAGES`, since Android 11 only shows the usage of apps this one can see. If access is
  taken away, the card comes back. The Tally place reads the same history again for its hours and
  apps (below), sorts it with the rules as they are, and keeps nothing.
- **Windows:** one switch. The tray app follows the foreground window with `SetWinEventHook`, reads
  the title every 15 seconds (browser tabs change without a window switch), and stops the clock after
  5 minutes without input (`GetLastInputInfo`), on lock and on sleep. A window in the Video category
  is not idle without input. The raw log stays in `%LOCALAPPDATA%\GoalMaker\tally\` for 30 days. Each
  line keeps the category it had when it was written, but the day totals sort the log again with the
  rules as they are, so a new rule re-sorts the days still being written (today, and yesterday after
  a start). The Tally page reads the log back for its hours and apps, the window still open
  included.

## Where it shows

- **The Tally place:** the switch (and on the phone the usage access card) at the top, filter chips
  (Phone, PC, and each category with time this week), then the day: today as one stacked bar by
  category on every device, or the day picked by tapping (clicking) its bar in the week, with Back to
  today. Under it, from this device's own record only: the day **by the hour**, a thin stacked
  column per clock hour from the hour the planning day starts (with 04:00, the last columns are
  00:00 to 03:00 of the next date), and the **apps**, for that day or the whole week: a row per
  category that opens to its apps with their minutes, and on the PC the sites a browser was on and
  the folders an editor had open (`window` in the vectors: the part of a browser's title just before
  its own name, or the editor's folder). Each app, site and folder offers **Make a rule**, which
  opens the rule sheet (panel on the PC) already filled in: the app on this device (a site as a title
  rule, a folder as a folder rule), in its current category for the owner to change. Saving or
  deleting a rule in the place counts today again at once. The place says that only this device's
  apps show, since the record never leaves it; with the other device's chip picked, it says where to
  look instead. The category chip narrows the hours and apps too. Then the week as stacked bars per
  day, **this week on each device** (a stacked bar for the phone and one for the PC, with their
  totals, from the synced days; it follows the category chip but not the device chips, since it
  compares the two), the **last 8 weeks** as stacked columns under the filter (`weeks` in
  the vectors, as in Stats), time per project this week, and the owner's own rules and categories to
  add, edit, merge and delete.
  The hours and the apps are pinned by `hours` and `apps` in `contracts/vectors/tally.json`: time two
  stretches share counts once, an hour keeps seconds, and the apps round each level to the nearest
  minute, leave out what rounds to none, and go most first, then by name. It is a place like any
  other: in the Places hub and the sidebar, pinnable. Bars grow in over the standard duration, or
  fade in with reduced motion; a screen reader hears each day's bar, the hours with time ("By the
  hour: 09:00 40 min, ...") and whether a category row is open.
- **Stats:** "Where the time went", twelve weeks of stacked columns by category (`weeks` in
  `contracts/vectors/tally.json`), shown once there is Tally time.
- **The review:** the period's time by category in the look-back, shown once there is Tally time.
- **The connector:** `get_time_tally(from, to, by = category | project | device)`, this week by
  default, and a `tally` section in `get_review_digest` (the minutes in all, and by category, project
  and device). A default category is named by its key (coding is Coding), since the deployed function
  doesn't read the shipped file; a test keeps the two equal. Comparing time with what was planned is
  left to Claude and the [Letter](letter.md).
