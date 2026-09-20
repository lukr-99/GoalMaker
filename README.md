# GoalMaker

Plan tomorrow's tasks and keep daily, weekly, monthly and yearly goals alive, on an Android phone and
a Windows PC that share one Supabase backend. Claude joins through a connector (from M3). The product
spec is [docs/spec.md](docs/spec.md); the plan is [docs/roadmap.md](docs/roadmap.md).

## Status

**M0 to M4 are built and M5 is under way.** What works today, on both apps unless it says otherwise:

- **Delivery (M0):** sign-in with an emailed 6-digit code, sessions kept across restarts, a signed
  update channel in Supabase Storage that both apps verify before installing, the migration chain
  with full-chain, isolated and row-security tests, CI for every part and a tag-driven release.
- **Sync (M1):** a SQLite replica with an outbox on each device, offline work, Realtime refresh
  ([docs/sync.md](docs/sync.md)).
- **Tasks (M2):** Today, Tomorrow and Inbox, the composer with shortcuts and a live preview, Plan
  tomorrow, repeating tasks ([docs/repeating.md](docs/repeating.md)), reminders with quiet hours and
  snooze ([docs/reminders.md](docs/reminders.md)), areas and tags with filters, a task's detail view
  with notes in light Markdown and a checklist, the searchable archive
  ([docs/archive.md](docs/archive.md)), the four switchable themes, and on Windows the tray's Today
  flyout and a global quick-add shortcut.
- **Claude (M3):** the connector, an MCP Edge Function Claude reaches through a secret link, with
  the activity log and undo ([docs/connector.md](docs/connector.md)).
- **Goals, habits, reviews, stats (M4):** the year to day goal cascade with three progress modes
  ([docs/goals.md](docs/goals.md)), habits with cadences, check-ins, streaks and a heatmap
  ([docs/habits.md](docs/habits.md)), the guided weekly and monthly review with a rotating prompt
  library and prompts that react to the period's data ([docs/reviews.md](docs/reviews.md)), review
  reminders, and the stats screen ([docs/stats.md](docs/stats.md)).
- **M5, in progress:** projects with a Backlog, To do, Doing and Done board
  ([docs/projects.md](docs/projects.md)), a week and month calendar with dragging
  ([docs/calendar.md](docs/calendar.md)), and Today and Habits widgets on Android
  ([docs/widgets.md](docs/widgets.md)), projects through the connector, where Claude finds the
  project by the repository or folder it is working in
  ([docs/connector.md](docs/connector.md)), sharing text or a link from any Android app into a task
  ([docs/composer.md](docs/composer.md)), and the pinnable Today and Habits mini windows on Windows
  ([docs/mini-windows.md](docs/mini-windows.md)). Still to come: Startup Profiles.

No release has been published yet; the cloud project and the signing keys still need the one-time
setup in [docs/setup/](docs/setup/).

## Parts

| Folder | What | Stack |
| --- | --- | --- |
| `android/` | The phone app | Kotlin, Jetpack Compose, Material 3 Expressive, supabase-kt |
| `windows/` | The PC app with tray | .NET 10 WPF, WPF UI, H.NotifyIcon, Supabase C# client |
| `supabase/` | Database migrations, tests, config | Postgres, Supabase CLI (local Docker stack) |
| `contracts/` | Rules both apps must implement the same way | JSON schemas and golden vectors |
| `tools/` | Repository, migration, release and signing tools | Python, PowerShell |

## Requirements

- Windows 10/11 for development (the Windows app and the installer are Windows only).
- JDK 21, the Android SDK (Gradle downloads compile SDK 37 by itself), an emulator or a phone.
- .NET SDK 10.0.200 or later in the 10.0 band ([windows/global.json](windows/global.json)).
- Node.js 22+ for the pinned Supabase CLI (`npm install`), Docker Desktop for the local stack.
- Python 3.11+, OpenSSL (ships with Git for Windows), Inno Setup 6 for the installer.

## Build and test

```powershell
npm install                                  # pinned Supabase CLI
npx supabase start                           # local stack on ports 553xx
python tools/supabase_migrations.py test     # migration chain, isolated steps, pgTAP

android\gradlew.bat -p android testDebugUnitTest assembleDebug lintDebug
dotnet test --solution windows\GoalMaker.slnx

python tools/validate_repository.py --root .
```

Running the apps against the local stack, including reading sign-in codes from the local mail viewer,
is in [docs/setup/local-development.md](docs/setup/local-development.md).

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md): the parts, module boundaries inside each app, composition
roots, data flow and delivery. Decisions are recorded in [docs/adr/](docs/adr/).

## Data safety

- **Source of truth:** the Supabase project (ADR 0002): profiles, areas, tags, tasks, steps,
  reminders and the activity log, each row visible only to its owner. Deleted rows stay as
  tombstones for 90 days so every device learns about them, then a nightly job purges them.
- **Device replicas:** each app keeps a SQLite copy with an outbox of unsent changes (one file per
  backend). Signing out pushes first and asks before discarding anything unsent.
- **Migrations:** immutable `supabase/migrations/0001_description.sql` files, locked by checksum in
  `supabase/migrations.lock.json`, each tested from `0001` and in isolation with fixtures.
- **Local data:** the Windows app keeps its session (DPAPI-encrypted), settings and crash log in
  `%LOCALAPPDATA%\GoalMaker` (`GoalMaker-dev` for dev builds); the Android app keeps its session,
  settings and its own `files/crash.log` in private app storage, excluded from Android backups.
- **Backup:** a versioned full export and a checked restore arrive in M6, before any destructive
  change is allowed. Until then, tasks live in the Supabase project (with its own backups on paid
  plans) and in each device's replica; there is no export yet.

## Delivery

- **Versions:** one `version.properties` for everything. Local builds are `X.Y.Z-dev`, never offered
  updates; release builds are plain `X.Y.Z`.
- **Android:** a signed APK, sideloaded. Debug is `com.goalmaker.app.debug`, release
  `com.goalmaker.app`.
- **Windows:** a per-user Inno Setup installer (framework-dependent, about 9 MB) that can start
  GoalMaker in the tray at sign-in.
- **Updates:** tag `vX.Y.Z` and the release workflow publishes the APK, the installer and a signed
  manifest to the private `releases` bucket, and drafts a GitHub Release. Apps check the bucket with
  the user's session (ADR 0004). A manual download from the GitHub Release always works.

Setup: [docs/setup/signing-and-releases.md](docs/setup/signing-and-releases.md).

## License

[PolyForm Noncommercial 1.0.0](LICENSE.md), the CodePrint default.
