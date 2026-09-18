# GoalMaker

Plan tomorrow's tasks and keep daily, weekly, monthly and yearly goals alive, on an Android phone and
a Windows PC that share one Supabase backend. Claude joins through a connector (from M3). The product
spec is [docs/spec.md](docs/spec.md); the plan is [docs/roadmap.md](docs/roadmap.md).

## Status

**M0, the delivery spine, is done.** What works today:

- Sign-in with an emailed 6-digit code on both apps (Supabase Auth), sessions kept across restarts.
- The Android app (Material 3 Expressive, Navigation 3) and the Windows app (WPF UI shell, tray icon,
  single instance, launch switches, remembered window position), each with Today and Settings
  placeholders, light/dark/system themes and a `-dev` identity for local builds.
- The update channel: a signed release manifest in a private Supabase Storage bucket, verified by
  both apps before they download and install an update (tested end to end on Windows, 0.1.0 → 0.1.1).
- The database migration chain with full-chain, isolated and row-security tests on a local stack.
- CI for every part and a tag-driven release workflow.

- Sync (M1): both apps keep a SQLite replica with an outbox, work offline, and sync through
  Supabase with Realtime refresh ([docs/sync.md](docs/sync.md)).
- Planning (M2, in progress): four switchable themes, Today, Tomorrow and Inbox, the composer with
  shortcuts and a live preview, Plan tomorrow, repeating tasks, reminders with quiet hours and
  snooze on both apps ([docs/reminders.md](docs/reminders.md)), and on Windows the tray's Today
  flyout and a global quick-add shortcut. Areas and tags management, task details and the archive
  are next.

The Claude connector comes in M3, goals and habits in M4. No release has been published yet; the cloud project and the signing keys still need the one-time setup in
[docs/setup/](docs/setup/).

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
  `%LOCALAPPDATA%\GoalMaker` (`GoalMaker-dev` for dev builds); the Android app keeps its session and
  settings in private app storage, excluded from Android backups.
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
