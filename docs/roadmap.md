# GoalMaker: roadmap

The spec is [docs/spec.md](spec.md). Each milestone lands in the order backend → Android → Windows,
and each has markdown issues under `.scratch/<milestone>/`.

## Process

1. Spec grilling (done: `docs/grilling/`)
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
9. **M5: projects, calendar, widgets, mini windows, share target** (under way since 2026-09-20:
   projects with their board, the week and month calendar with dragging, the Android Today and
   Habits widgets, projects through the connector (checked through the endpoint against the local
   stack), share to GoalMaker and the Windows mini windows are built; Startup Profiles remains)
10. M6, then v1.0
11. M7 and the post-v1 list

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

Backup and restore (versioned JSON export, checked restore, weekly automatic export), live updates
through the channel, hardening, accessibility pass, first release.

### M7: quick chat

The `assistant` Edge Function over the shared tool module with the Gemini free tier behind a
provider interface; the composer switches between quick-add and chat.

## After v1

- Connector OAuth 2.1 with a sign-in page on Cloudflare Pages (`web/`)
- Read-only calendar feeds
- Windows 11 Widgets board
- Quick Settings tile, voice capture
- Czech translation
- Other quick-chat providers (for example a local Ollama model)
