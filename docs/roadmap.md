# GoalMaker: roadmap

The spec is [docs/spec.md](spec.md). Each milestone lands in the order backend → Android → Windows,
and each has markdown issues under `.scratch/<milestone>/`.

GoalMaker is built for the owner's personal use only (ADR 0011): nothing shared or multi-user is
planned, and the shared habit challenges idea was taken off the roadmap on 2026-09-28.

## Process

1. Spec grilling (done; the record stays with the owner, not in the repository)
2. Spec, roadmap, glossary and ADRs (done)
3. **M0: delivery spine** (built 2026-09-18; the first push and the owner's one-time setup remain)
4. **Design questionnaire** (answered 2026-09-18: four switchable themes, Track by default;
   [docs/design/spec.md](design/spec.md), ADR 0008)
5. **M1: data and sync** (built 2026-09-18)
6. **M2: tasks everywhere** (built 2026-09-19, checked on both apps against the local stack)
7. **M3: connector** (built 2026-09-19: the MCP Edge Function with tools and ritual prompts, links
   and the activity log with undo in both apps, weekly summaries; checked on both apps and through
   the endpoint against the local stack)
8. **M4: goals, habits, reviews, stats** (built 2026-09-20: the goal cascade and progress, habits
   with check-ins, streaks and the heatmap, the guided weekly and monthly review with the prompt
   library and the data-reactive prompts, review reminders, the stats screen, and goals and habits
   through the connector; checked on both apps and through the endpoint against the local stack)
9. **M5: projects, calendar, widgets, mini windows, share target** (built 2026-09-20: projects with
   their board, the week and month calendar with dragging, the Android Today and Habits widgets,
   projects through the connector, share to GoalMaker, the Windows mini windows and Add to Startup
   Profiles; checked through the endpoint against the local stack)
10. **M6: v1.0** (planned as issues 2026-09-20; 1.0.0 published 2026-09-21, 1.2.0 on 2026-09-23 from
    the public repository; backup and restore, the problems place and the release are built, and
    the close-out below is what is left)
11. **M7: quick chat** (next; issues still to write in `.scratch/m7-quick-chat/`)
12. **M8: Letter, Tally, Wants** (planned as issues 2026-09-28 in `.scratch/m8-letter-tally-wants/`)
13. The post-v1 list

## Milestones

### M0: delivery spine

Repository layout, CI for every part, local Supabase with migration and row-security tests, email
code sign-in on both apps, debug/release identity and version, Android release signing, Windows
installer, and the update channel (signed manifest in a private Storage bucket, verified by both
apps). Issues: `.scratch/m0-delivery-spine/`.

Owner's one-time setup before the first release (guides in `docs/setup/`): create the cloud
project and push the schema, set the Supabase secrets, create the Android release key and the
manifest signing key, commit the manifest public key, protect `main`.

### M1: data and sync

Core schema (areas, tags, tasks, steps, reminders, activity log), the shared SQLite replica (ADR 0007) on both apps, the
outbox and pull sync, Realtime refresh, tombstone purge, the sync-merge contract vectors. A plain
task list on Today proves the round trip on both apps. Issues: `.scratch/m1-data-and-sync/`.

### M2: tasks everywhere

Today, Tomorrow, Inbox, the composer with shortcuts (contract vectors), Plan tomorrow, repeating
tasks, reminders and notifications on both apps, the tray, the global hotkey, single instance and
launch switches, areas and tags, and the four themes from the design spec. Issues:
`.scratch/m2-tasks-everywhere/`.

### M3: connector

The MCP server on Edge Functions with the secret link, the shared tool module, ritual prompts,
rotate and revoke in settings, the activity log with undo, the routine prompt for weekly summaries.

### M4: goals, habits, reviews, stats

The goal cascade and progress modes, habits with check-ins and streaks (contract vectors), weekly and
monthly reviews with the prompt library and data-reactive prompts, mood and energy, stats.

### M5: projects, calendar, widgets, mini windows, share target

Projects with the board, milestones and priorities, the calendar view, Today and Habits widgets,
pinnable mini windows, share to GoalMaker, Add to Startup Profiles. Issues:
`.scratch/m5-projects-calendar-and-desktop/`.

### M6: v1.0

Backup and restore (a versioned JSON export both apps read, a checked restore that merges and never
wipes, and the weekly automatic export on Windows), updates through the channel proved end to end on
both apps, the accessibility pass, hardening, and the first release with the owner's one-time setup.
Where a problem shows (one place in Settings with a quiet mark, not across the top of Today).
Issues: `.scratch/m6-backup-updates-and-v1/`.

**Close-out (decided 2026-09-28).** For a personal app M6 is done when three things hold:

1. The main PC, which already runs an installed release, updates itself once from the latest GitHub
   Release (M6-04 and M6-07).
2. Android shows the "replica won't open" screen Windows already has, instead of closing (M6-06).
3. A session the server refuses while an app is open sends the owner to sign-in on both apps without
   losing the outbox (M6-06).

Everything else M6 listed moves to "After v1" below: postponing an update, the rest of the
accessibility pass (keyboard reach on Windows, Android focus order, the largest text size), a full
disk and sync under pressure.

### M7: quick chat

The `assistant` Edge Function over the shared tool module with the Gemini free tier behind a
provider interface; the composer switches between quick-add and chat.

### M8: Letter, Tally, Wants

Three personal extensions, each released on its own, after a navigation that has room for them.
Issues: `.scratch/m8-letter-tally-wants/`.

- **Navigation (M8-01, M8-02):** the tab bar and the overflow dots are full. A prototype picks a
  navigation that scales, then both apps get it before any new place arrives.
- **Wants (M8-03 to M8-06; 1.3.0 shipped the navigation and M8-03 to M8-05 on 2026-09-28, the connector tools of M8-06 follow):** a wishlist where every want carries
  its reason and waits out a cooldown set by its price (thresholds the owner can change), one daily
  notification for wants that became ready, a Wants place with Cooling, Ready and Decided filters,
  `/want` in the composer, a Wants block in stats, and connector tools so Claude can check prices
  with its own web search and record the decision ([docs/wants.md](wants.md)).
- **Letter (M8-07 to M8-09; 1.4.0 shipped it on 2026-09-30, with the wants tools of M8-06):** a Claude routine writes a weekly letter from
  GoalMaker's `get_review_digest` and the owner's other apps' own connectors, saved as the review's
  summary and read as the first step of the guided review (ADR 0012, [docs/letter.md](letter.md)).
- **Tally (M8-10 to M8-14, release 1.5.0):** where time actually went on the phone and the PC, as
  daily minutes per category (and per project on the PC), with raw data kept on each device (ADR
  0013, [docs/tally.md](tally.md)); a Tally place, blocks in stats and the review, and
  `get_time_tally` in the connector.

## After v1

From M6's close-out (2026-09-28):

- Postponing an update, and refusing a bad manifest shown on a real device
- The rest of the accessibility pass: keyboard reach on Windows (mini windows, tray flyout,
  quick-add), Android focus order and the composer's chips, both apps at the largest text size
- Hardening: a full disk, sync under pressure (a device off for weeks, two devices on one row, the
  purge crossing a pull)
- Habit reminders with check-in from the notification
- A yearly review you can start, and the January nudge to set yearly goals (story 66)
- UI tests: Maestro flows, Compose UI tests, a Windows start-up smoke test
- Small spec gaps: the 1-hour snooze on Android, filters on Projects, Calendar and Archive,
  important reminders through Do Not Disturb

From the spec:

- Connector OAuth 2.1 with a sign-in page on Cloudflare Pages (`web/`)
- Read-only calendar feeds
- Windows 11 Widgets board
- Quick Settings tile, voice capture
- Czech translation
- Other quick-chat providers (for example a local Ollama model)
