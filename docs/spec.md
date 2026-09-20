# GoalMaker: spec (v1)

**Platforms:** Android phone (Kotlin + Jetpack Compose) and Windows 10/11 (WPF on .NET 10)
**Backend:** Supabase (Postgres, Auth, Realtime, Storage, Edge Functions)
**Claude:** a remote MCP connector used by Claude apps on the owner's Claude plan; no Anthropic API
**Distribution:** sideloaded signed APK and a per-user Windows installer, updated from a private
Supabase Storage bucket
**License (code):** PolyForm Noncommercial 1.0.0 (CodePrint default)

The decisions behind this spec, with the owner's answers, are in `docs/grilling/` (rounds 1 to 4
and the summary). Domain terms are defined in [CONTEXT.md](../CONTEXT.md).

## Problem Statement

Lukáš wants one place to plan tomorrow's tasks and to keep daily, weekly, monthly and yearly goals
alive. Plans made on the phone must be visible and actionable on the PC and the other way round,
with reminders that pop up on both. Claude should be able to read and change the plan from any
Claude app, including the Claude phone app and Claude Code, without paying for the Anthropic API.
Next to personal goals, he also wants a small Jira for his code projects, so ideas and bugs can be
captured on the go. Existing apps split these jobs across several tools, don't talk to Claude, and
don't have a Windows tray companion.

## Solution

GoalMaker is two full apps (a rich Android app and a Windows app that also lives in the tray) on a
shared Supabase backend. Goals form a cascade (year → month → week → day). Tasks are concrete
actions with a planned day, a deadline, reminders and repeats. Habits are their own kind with
check-ins and streaks. Code projects have a Backlog / To do / Doing / Done board of tasks, ideas and
bugs. Guided rituals structure the work: **Plan tomorrow** every evening, and weekly and monthly
**reviews** that look back with charts, rotate reflection prompts and set the next period's goals.

Both apps share a chat-app layout: navigation on the side or bottom, a main pane, and a
**composer** bar where typing shortcuts (`tomorrow 17:00 #tag @Area ! ? +Project`) create items
instantly. Reminders are scheduled locally on each device and fire on both. Claude connects through
a remote MCP server (the **connector**) that exposes GoalMaker tools and ritual prompts. A quick chat
through the Gemini free tier follows as M7.

## User Stories

### Capture and organize

1. As the owner, I want to type a task into the composer and press Enter, so that capturing takes
   seconds.
2. As the owner, I want `tomorrow 17:00` (and similar date words) in the composer to set the planned
   day and time, so that I don't open a date picker.
3. As the owner, I want `#tag`, `@Area`, `!`, `?` and `+Project` in the composer to set tags, area,
   top priority, idea type and project, so that one line files an item completely.
4. As the owner, I want the composer to show a live preview of what my shortcuts mean, so that I
   can trust it before pressing Enter.
5. As the owner, I want a task without a planned day and without an area to land in the Inbox, so
   that nothing I capture gets lost.
6. As the owner, I want to sort Inbox items into days, areas, goals or projects later, so that
   capture and organizing are separate moments.
7. As the owner, I want colored areas (Health, School, Work, Personal, ...), so that I can see at a
   glance which part of my life an item belongs to.
8. As the owner, I want free tags on tasks, goals, habits and project items, so that I can group
   things across areas.
9. As the owner, I want to filter any list by area and tag, so that I can focus.
10. As the owner, I want to share text or a link from any Android app into GoalMaker, optionally
    into a project, so that ideas from the browser or chats become items.
11. As the owner, I want a global hotkey on Windows that opens a quick-add box, so that I can capture
    without leaving what I'm doing.

### Tasks

12. As the owner, I want a task to have a title and notes (with light Markdown), so that I can keep
    context with it.
13. As the owner, I want to set a planned day with an optional time, separate from an optional
    deadline, so that "when I'll do it" and "when it's due" don't get mixed up.
14. As the owner, I want a checklist of sub-steps inside a task, so that small multi-step jobs stay
    one task.
15. As the owner, I want to link a task to at most one goal and at most one project, so that
    progress and project boards stay clear.
16. As the owner, I want repeating tasks (daily, weekdays, weekly on chosen days, every N days,
    monthly on a date), so that routine work appears by itself.
17. As the owner, I want the next occurrence of a repeating task to appear when I finish or skip the
    current one, so that the list never fills with copies.
18. As the owner, I want completed tasks kept in an archive I can search, so that I can see what I
    did.
19. As the owner, I want to undo a completion or a deletion, so that mistakes are cheap.

### Today and Plan tomorrow

20. As the owner, I want the app to open on Today in the morning, showing planned tasks, top
    priorities, due deadlines, habits for today and reminders, so that I know what the day holds.
21. As the owner, I want a Tomorrow list I can fill whenever I like, so that ideas for tomorrow
    have a place.
22. As the owner, I want an evening reminder that starts the Plan tomorrow ritual, so that planning
    becomes a habit.
23. As the owner, I want Plan tomorrow to walk me through today's unfinished tasks and make me
    choose for each one (move to tomorrow, give it a date, drop it), so that nothing rolls over
    silently.
24. As the owner, I want to pick my top priorities for tomorrow during the ritual, so that the
    morning starts focused.
25. As the owner, I want the day to roll over at 04:00 (changeable), so that planning after midnight
    still means the right "tomorrow".
26. As the owner, I want day-level items to keep their calendar date when I travel, so that time
    zones don't move my plans.

### Goals

27. As the owner, I want goals for a year, a month, a week or a day, so that I can plan at every
    scale.
28. As the owner, I want to link a goal to a parent goal in a longer period, so that "3 runs this
    week" visibly serves "Run a half marathon this year".
29. As the owner, I want linking to be optional, so that a quick weekly goal needs no parent.
30. As the owner, I want each goal to measure progress as done/not done, as the share of its linked
    tasks that are done, or as a number with a target, so that progress fits the goal.
31. As the owner, I want to log amounts by hand on a numeric goal ("+5 km"), so that I can record
    progress that isn't a habit.
32. As the owner, I want check-ins of a linked habit with the same unit to add to a numeric goal
    automatically, so that a 5 km run moves "Run 80 km in September".
33. As the owner, I want a progress bar or ring per goal, so that I can see where I stand.
34. As the owner, I want to see the goal cascade as a tree and per period, so that I understand how
    my goals connect.
35. As the owner, I want "copy last week's goals" when planning a new week, so that recurring
    intentions are one tap.

### Habits

36. As the owner, I want habits that run every day, on chosen weekdays, N times a week or N times a
    month, so that each habit matches its rhythm.
37. As the owner, I want a habit to be a simple check, a count with a target (8 glasses) or an amount
    with a unit (30 minutes, 5 km), so that I can track what matters.
38. As the owner, I want to check in from the app, the Habits widget or the reminder notification,
    so that logging takes one tap.
39. As the owner, I want streaks that count periods met (days, or weeks for "3 times a week"), so
    that my streak means something.
40. As the owner, I want to mark a period as skipped (sick, travel) without breaking the streak, so
    that honest exceptions don't punish me.
41. As the owner, I want to pause a habit for a holiday and resume without losing the streak, so
    that breaks are planned, not failures.
42. As the owner, I want a habit heatmap, so that I can see consistency over months.

### Projects (a small Jira)

43. As the owner, I want a Projects tab for my code projects with a name, description, area, status
    (active, paused, done) and notes, so that coding work has its own home.
44. As the owner, I want to link a project to its GitHub URL and local folder, so that tools
    (including Claude Code) can find the right project.
45. As the owner, I want project items typed as Task, Idea or Bug, so that I can capture ideas and
    bugs quickly.
46. As the owner, I want a Backlog / To do / Doing / Done board on Windows and a grouped list on the
    phone, so that I can see and move work.
47. As the owner, I want priorities low, normal, high and urgent on project items, so that the most
    important work rises.
48. As the owner, I want optional milestones per project, so that I can group items like M0 to M6.
49. As the owner, I want new ideas to land in Backlog, so that they don't clutter To do.
50. As the owner, I want a project item with a planned day to also show in Today, so that project
    work is part of my day.

### Reminders and notifications

51. As the owner, I want several reminders per task, each at a fixed time or relative to the task's
    time, so that I get warned early and on time.
52. As the owner, I want reminders to pop up on both my phone and my PC, so that I see them wherever
    I am.
53. As the owner, I want dismissing or completing a reminder on one device to clear it on the other,
    so that I don't handle it twice.
54. As the owner, I want Done and Snooze (10 minutes, 1 hour, tomorrow morning) on every
    notification, so that I can act without opening the app.
55. As the owner, I want ritual reminders (Plan tomorrow, weekly review, monthly review) at times I
    choose, so that the rituals happen.
56. As the owner, I want quiet hours that hold back ordinary reminders, so that nights stay quiet.
57. As the owner, I want important reminders to bypass quiet hours and ring alarm-style until I act,
    so that critical things are never missed.
58. As the owner, I want phone reminders to fire even if sync is behind or the phone rebooted, so
    that reminders are reliable.

### Reviews and reflection

59. As the owner, I want a guided weekly review (look back, handle open tasks, reflect, set next
    week's goals, glance at next week's calendar), so that each week has a clear end and start.
60. As the owner, I want a monthly review with the same steps one level up, so that months connect
    to weeks.
61. As the owner, I want the look-back to show a habit heatmap, goal charts, highlights (longest
    streak, best day, strongest area) and a comparison with the last period, so that reviews are
    rich.
62. As the owner, I want reflection prompts drawn from a rotating library, so that reviews don't
    repeat the same text every week.
63. As the owner, I want prompts that react to my data (a missed habit, a task that keeps sliding, a
    goal ahead of plan), so that reflection is about my real week.
64. As the owner, I want to rate mood and energy 1 to 5 each week and see them charted, so that I
    notice patterns.
65. As the owner, I want to read past reflections, so that I can see how I've changed.
66. As the owner, I want a nudge in January to set yearly goals, so that the year starts with
    intent.

### Stats

67. As the owner, I want a stats screen with tasks completed per week, goals hit per month and habit
    success, so that I can see trends.

### Calendar

68. As the owner, I want a week and month calendar of planned tasks, deadlines and reminders, so that
    I can plan across days.

### Claude

69. As the owner, I want to add GoalMaker to Claude as a connector once on claude.ai, so that it
    works in Claude on the web, the desktop, my phone and Claude Code.
70. As the owner, I want Claude to read Today, Tomorrow, Inbox, goals, habits and projects, and to
    search, so that it can answer questions about my plans.
71. As the owner, I want Claude to add, edit, complete and move tasks, add and edit goals, check in
    habits and set reminders, so that I can manage my plan by talking.
72. As the owner, I want every change Claude makes to show "by Claude" in an activity log with undo,
    so that I stay in control.
73. As the owner, I want Claude's deletes to be soft and confirmed in the conversation first, so that
    nothing disappears by accident.
74. As the owner, I want ready-made Plan tomorrow, Weekly review and Monthly review prompts in the
    Claude app, so that Claude can walk me through a ritual with my real data.
75. As the owner, I want a scheduled Claude routine (or another MCP-capable assistant) to write a
    weekly review summary into GoalMaker, so that the summary is waiting for me.
76. As the owner, I want Claude Code to add a task to the project whose repository I'm working in,
    so that coding ideas land in the right backlog.
77. As the owner, I want to rotate or revoke the connector link from either app, so that a leaked
    link can be killed at once.

### Windows specifics

78. As the owner, I want the Windows app to have every feature the phone has, in a desktop layout,
    so that I can plan fully at my PC.
79. As the owner, I want a tray icon with a Today flyout, so that today's plan is one click away.
80. As the owner, I want pinnable mini windows (Today, Habits) I can keep on the desktop or on top,
    so that I see my day while working.
81. As the owner, I want GoalMaker to start in the tray when I sign in to Windows, on by default, so
    that PC reminders work.
82. As the owner, I want launch switches (`--tray`, `--open`, `--mini`) and `goalmaker://` links, so
    that Startup Profiles and shortcuts can open exactly what I want.
83. As the owner, I want launching GoalMaker again to reuse the running instance, so that I never
    get two copies.
84. As the owner, I want "Add to Startup Profiles" in settings when Startup Profiles is installed, so
    that GoalMaker joins my login profiles through that app's own consent window.

### Android specifics

85. As the owner, I want a Today widget where I can tick off tasks, so that I don't open the app for
    small wins.
86. As the owner, I want a Habits widget for one-tap check-ins, so that logging habits is instant.
87. As the owner, I want a quick-add widget that opens a composer over whatever I'm in, so that a
    thought reaches the Inbox or today without opening the app.
88. As the owner, I want a bold, energetic look with satisfying motion and a small celebration when
    I hit a goal or a streak milestone, so that progress feels rewarding.

### Account, data and delivery

88. As the owner, I want to sign in with a 6-digit code sent to my email, so that there is no
    password to manage.
89. As the owner, I want both apps to work offline and sync when back online, so that bad signal
    never blocks me.
90. As the owner, I want the other device to update live while it's open, so that both screens agree.
91. As the owner, I want a versioned full export of all my data and a checked restore in both apps,
    so that I own my data.
92. As the owner, I want the Windows app to write an automatic weekly export to a folder I choose, so
    that I always have a recent backup.
93. As the owner, I want both apps to find, verify and install their own updates from a private
    channel using my sign-in, so that I stay current without a store.
94. As the owner, I want a manual download path for updates, so that a broken updater never strands
    me.
95. As the owner, I want light, dark and system themes, so that the apps suit the time of day.
96. As the maintainer, I want a debug build with its own app ID and a `-dev` version that talks to a
    local Supabase stack by default, so that testing never touches real data.
97. As the maintainer, I want CI that validates the repository, runs the database migration chain
    and row-security tests, builds and tests both apps, and checks the shared contract examples, so
    that every change is proven.
98. As the maintainer, I want a tag-driven release that builds the signed APK and installer,
    publishes a signed manifest to the update bucket and drafts a GitHub Release, so that shipping is
    one command.

## Implementation Decisions

### Architecture

- **One repository**, CodePrint baseline: `android/`, `windows/`, `supabase/`, `contracts/`, and
  `web/` later (the OAuth sign-in page). Domain code in each app is pure and independent of UI,
  storage and network. One composition root per app, constructor injection, one top-level type per
  file.
- **Source of truth:** Supabase Postgres. Each device keeps a SQLite replica with one shared schema
  (`replica/migrations/`) and an outbox of pending changes. See ADR 0002, ADR 0007 and
  [docs/sync.md](sync.md).
- **Row security:** every user table has `owner_id` and row-level security that allows only
  `auth.uid() = owner_id`. The connector acts as the owner under the same policies.

### Data model (v1)

- `profiles` (one per user: display name, time zone, day rollover hour, quiet hours, ritual times).
- `areas`, `tags` and item-tag link tables.
- `goals`: horizon (`year | month | week | day`), period start date, optional parent goal (must be a
  longer horizon), progress mode (`done | tasks | number`), target and unit for numeric goals,
  status.
- `goal_entries`: manual amounts logged on numeric goals.
- `tasks`: title, notes, planned date, planned time, deadline date, top priority, status, completed
  at, area, goal, project, project fields (item type `task | idea | bug`, board column `backlog |
  todo | doing | done`, priority `low | normal | high | urgent`, milestone), recurrence (RRULE
  subset), recurrence series id.
- `task_steps`: checklist items.
- `habits`: cadence (`daily | weekdays | per_week | per_month` with days or count), measure
  (`check | count | amount`), target and unit, linked goal, paused ranges.
- `habit_checkins`: date, value, skipped flag.
- `projects`: name, description, area, status, repository URL, local folder, notes.
- `project_milestones`.
- `reminders`: owner item, fire time (absolute or offset from the task's time), important flag,
  state (`pending | fired | dismissed | done | snoozed`) with snoozed-until.
- `reviews`: kind (`weekly | monthly | yearly`), period, mood, energy, reflections (prompt id plus
  answer), summary (including connector-written summaries).
- `activity_log`: actor (`owner | claude | system`), action, entity, before and after snapshots,
  undone flag.
- `connector_links`: hashed secret, created at, last used at, revoked at.
- Every synced row carries `id` (UUID made on the device), `owner_id`, `created_at`, `updated_at`
  (set by the server), `deleted_at` (tombstone) and `version`.

### Sync

- Push: the outbox sends changes in order; each write is an idempotent upsert keyed by `id`.
- Pull: fetch rows with `updated_at` after the device's watermark, per table, in pages.
- Conflicts: last writer wins per row using the server's `updated_at`. The merge rule is a shared
  contract with golden vectors run by Kotlin and C#.
- Deletes are tombstones kept 90 days, then purged by a scheduled job. A device whose watermark is
  older than the purge horizon does a full resync.
- Realtime subscriptions refresh an open app. Android also syncs in the background every 15
  minutes with WorkManager.

### Reminders

- Each device schedules reminders locally from its replica: Android exact alarms (`AlarmManager`),
  Windows through the tray app's timer plus toast notifications (ADR 0009).
- The phone stores every upcoming reminder in its database and reschedules on boot, time or time
  zone change and app update.
- Reminder state changes (dismissed, done, snoozed) sync, and the other device cancels its copy.
- Quiet hours and the important flag are evaluated on the device at fire time.

### Composer and shortcuts

- A pure parser turns a line into an item draft: date words (`today`, `tomorrow`, weekdays, `in 3
  days`, `next week`, ISO dates), times (`17:00`, `5pm`), `#tag`, `@Area`, `!` (top priority), `?`
  (idea), `+Project`, `every ...` (repeat presets). `/` starts a command (`/plan`, `/review`,
  `/habit`, ...).
- Implemented in Kotlin and C#; both must pass the same golden vectors in `contracts/`.

### Shared rules with golden vectors

Recurrence expansion, streak calculation, day rollover, composer parsing, sync merge, semantic
version comparison and release-manifest verification each have a versioned vector file in
`contracts/vectors/`, run by both apps' tests.

### Claude connector

- A remote MCP server as a Supabase Edge Function (TypeScript, official MCP TypeScript SDK,
  Streamable HTTP).
- v1 authentication: a secret link (`.../connector/<secret>`). The secret is stored as a hash in
  `connector_links`; rotate and revoke in settings. The function resolves the owner and acts
  through that owner's row security; it never runs queries with the service role on user tables.
  Rate-limited. See ADR 0003.
- Tools: read and search; add, edit, complete, move tasks; goals; habit check-ins; reminders; soft
  delete after confirmation; save review summary. Prompts: Plan tomorrow, Weekly review, Monthly
  review.
- One shared tool module in `supabase/functions/_shared/`, reused by the M7 quick chat.
- Later: OAuth 2.1 through Supabase Auth's OAuth server with a static sign-in page on Cloudflare
  Pages (`web/`).

### Quick chat (M7)

An `assistant` Edge Function runs a tool loop over the shared tool module using the Gemini free
tier, behind a provider interface so other models (such as a local Ollama model on the PC) can be
tried later. Not part of v1.

### Reviews content

A versioned prompt library (about 100 prompts with categories and triggers) ships as a content file
in `contracts/content/`, used by both apps. Data-reactive prompts are rules over the period's
statistics.

### Android

Kotlin, Jetpack Compose with **Material 3 Expressive** (pinned alpha, ADR 0005), Navigation 3,
the shared SQLite replica on `androidx.sqlite` (ADR 0007), WorkManager, Glance widgets, supabase-kt, Vico charts, Haze blur, Kizitonwose Calendar,
Reorderable, Konfetti, a Markdown renderer. Min SDK 26, compile SDK 37 (the Expressive alpha needs
it), target SDK 36 until Android 17's behavior changes are reviewed. Debug build:
`com.goalmaker.app.debug`, version suffix `-dev`, local stack by default.

### Windows

.NET 10 WPF with **WPF UI** (Fluent shell), H.NotifyIcon (tray), LiveCharts2, toast notifications
through the Windows SDK projection (unpackaged, without the Windows App SDK; ADR 0009),
NHotkey (global hotkey), Markdig, the Supabase C# client, the shared SQLite replica through
Microsoft.Data.Sqlite (ADR 0007).
Published framework-dependent to stay under the update channel's 50 MB file limit (ADR 0004).
`dotnetlib` was checked; see ADR 0006. Single instance, launch
switches, `goalmaker://` links, Startup Profiles registration through its public contract.

### Supabase operations

The Supabase CLI (through `npx`) runs a local Docker stack for development and CI. Migrations are
immutable `0001_description.sql` files in `supabase/migrations/`; every migration gets a full-chain
test and an isolated N-1 → N test with fixtures; row security is tested with pgTAP. One free cloud
project; deploys run through the CLI. See ADR 0001.

### Delivery and updates

- Android: signed APK, sideloaded. Windows: per-user Inno Setup installer.
- A tag-driven workflow builds both, writes a release manifest (version, per-platform artifact path,
  size, SHA-256) signed with an Ed25519 key, uploads artifacts and manifest to a private Supabase
  Storage bucket, and drafts a GitHub Release. See ADR 0004.
- Apps: release source (manifest from Storage with the user's session) → signature check with the
  embedded public key → version policy → download → SHA-256 check → installer launch. A manual
  download path always exists.

## Testing Decisions

- A good test checks behavior through the highest practical seam and never depends on
  implementation details, clocks, randomness, network or devices without a controllable seam.
- **Contract vectors:** every shared rule above has one vector file; Kotlin and C# tests load the
  same file. Adding a case means both apps must pass it.
- **Database:** the migration harness runs the full chain from `0001` and an isolated N-1 → N step
  with a fixture for every migration, on the local stack, in CI. pgTAP tests prove row security
  (owner can, stranger can't, anonymous can't) for every table.
- **Connector:** tool tests against the local stack through the MCP endpoint.
- **Android:** JVM unit tests for domain and application code, Robolectric for Android adapters,
  Compose UI tests for key screens, a few Maestro flows (prior art: Tarot2Go).
- **Windows:** xUnit tests for domain, application and view models; a start-up smoke test.
- Prior art: Tarot2Go (Room migrations, updater seams), SubTrackr (sync over Supabase), CodePrint
  (contract vectors, migration harness).

## Out of Scope (v1)

- Other users, sharing, collaboration.
- Any paid LLM API; in-app chat before M7.
- OAuth for the connector (secret link in v1).
- Firebase or any push service.
- GitHub issue sync.
- Read-only calendar feeds (right after v1).
- Windows 11 Widgets board (after v1).
- Quick Settings tile and voice capture (after v1).
- Czech translation (strings are ready for it).
- Play Store or Microsoft Store distribution.
- Sprints, story points and epics in projects; a game layer.

## Further Notes

- The owner has no Anthropic API account and doesn't want API costs. Claude features must work on
  the Claude plan through the connector.
- The Gemini free tier is chosen for M7 with open eyes: EU users get paid-tier data handling, but
  Google's terms reserve paid services for apps offered to EU users, and the free tier can change.
- A free Supabase organization allows two active projects; GoalMaker uses one, SubTrackr the other.
