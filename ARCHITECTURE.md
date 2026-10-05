# GoalMaker architecture

## Context

One owner uses GoalMaker on an Android phone and a Windows PC. Supabase (Postgres, Auth, Storage,
Realtime, Edge Functions) is the source of truth; each app keeps a SQLite replica with an outbox, so
it works offline and syncs when it can (ADR 0002, ADR 0007, [docs/sync.md](docs/sync.md)). Claude
reaches the data through the connector, an MCP Edge Function that acts as the owner under row
security (ADR 0003, [docs/connector.md](docs/connector.md)). Releases reach both apps through a
signed update channel on GitHub Releases (ADR 0010).

```text
  Android app  ──┐                           ┌── Claude apps (MCP, the connector's secret link)
  (Kotlin)       │  HTTPS, user session      │
                 ├──────────► Supabase ◄─────┘
  Windows app  ──┤   Auth · Postgres (RLS) · Realtime · Edge Functions (connector, assistant)
  (.NET/WPF)     │              ▲
                 │              │ migrations, deploys (CLI)
                 │       supabase/ folder
                 │
                 └──────────► GitHub Releases ◄── release workflow (CI): artifacts + signed manifest
                   (updates, no sign-in)
```

## Modules and dependencies

Both apps follow the same shape (CodePrint): domain ← application ← adapters, with one composition
root that builds the graph and hands it over through constructors. Domain and application code
never touch UI, storage, network or platform APIs.

```text
UI (Compose / WPF) ───► application ───► domain
        │                    ▲
        ▼                    │
adapters (Supabase, crypto, files, installers) ───┘
composition root creates everything
```

### Android (`android/app`, package `com.goalmaker.app`)

- `domain/`: `version/SemanticVersion`, `update/` (manifest, parser, update policy),
  `navigation/PlaceRules` (pinned places, ADR 0014),
  `notes/LightMarkdown`, `share/SharedCapture` (what another app shared, as a composer line and
  notes), `account/` (email, sign-in code), `settings/` (`ThemeMode`, `SettingsFieldRules` for the
  typed times and the backend address, and `SettingsPageRules`: the Settings page's current
  section, 4+ rule, jump timing and hints, held to `contracts/vectors/settings.json`), `sync/`
  (`SyncRules`, the synced-table catalog, outbox entries, cursors). Pure Kotlin.
- `application/`: ports and use cases: `auth/AuthGateway`, `update/UpdateService` with the
  `ReleaseChannel`, `SignatureVerifier` and `UpdateInstaller` seams, `settings/SettingsStore`,
  `sync/` (`Replica` and `RemoteTables` ports, `SyncEngine`, `SyncCoordinator`),
  `planning/`: the lists that read and write the replica (`TaskList`, `StepList`, `GoalList`,
  `HabitList`, `ReviewList`, `ProjectList`, `ReminderList`, `RitualRunList`, `LifeGoalList`, `EventList`) and the
  rules beside them (`ListRules`, `PlanRules`, `ArchiveRules`, `GoalRules`, `HabitRules`,
  `ReviewRules`, `ReviewLookBack`, `StatsRules`, `ProjectRules`, `CalendarRules`, `ReminderRules`,
  `LifeGoalRules`, `WhyReminder`, `EventRules`),
  `assistant/` (the `AssistantClient` port for the quick chat and its replies), `composer/`
  (`QuickAddLines`: what the bottom bar reads from a want, habit or goal line).
- `data/`: `SupabaseAuthGateway`, `GitHubReleaseChannel`, `EcdsaSignatureVerifier`,
  `ApkInstallerLauncher` (FileProvider), `SharedPreferencesSettingsStore`, `replica/`
  (`SqliteReplica` on the bundled SQLite driver, `ReplicaMigrator`, `SqlScript`), `sync/`
  (`PostgrestRemoteTables` over Ktor, `SupabaseChangeFeed`, `SyncWorker` and
  `WorkManagerSyncScheduler`), `planning/` (`AlarmReminderScheduler`, `ReminderNotifications`,
  `ReminderReceiver`, and the life goal picture adapters `FilePictureFiles` and
  `SupabasePictureCloud`), `activity/` and `connector/` (PostgREST readers), `assistant/SupabaseAssistantClient`,
  `diagnostics/CrashLog`.
- `ui/`: `places/` (the Places hub and its live tiles, ADR 0014),
  `theme/` (Material 3 Expressive, semantic tokens, pinned alpha per ADR 0005), `components/`
  (the shared ring, chips, emoji field and logo), `signin/`, `lists/` (Today, Tomorrow, Inbox),
  `chat/` (the quick chat's switch and thread in the composer),
  `plan/`, `task/`, `goals/`, `habits/`, `review/`, `stats/`, `projects/`, `calendar/`, `archive/`,
  `activity/`, `areas/`, `connector/`, `settings/`, `share/` (the sheet a share from another app
  opens), `widget/` (the Glance home screen widgets and the Motivation widget's configure screen),
  `nav/` (Navigation 3 back stack).
- `composition/AppGraph` is the composition root, owned by `GoalMakerApplication`, which also hands
  WorkManager a worker factory wired to it.

### Windows (`windows/`)

- `GoalMaker.Core` (net10.0): the same domain and application code as Android's, in C#
  (`Versioning`, `Updates`, `Account`, `Auth`, `Settings`, `Backend`, `About`, `Sync`, `Planning`,
  `Notes`, `Composer` (the task grammar and `QuickAddLines` for the bottom bar's want, habit and
  goal lines), `Startup` (starting with Windows and the Startup Profiles contract), `Assistant` (the
  quick chat's client port and replies)).
- `GoalMaker.Infrastructure` (net10.0-windows): `SupabaseAuthGateway`, a DPAPI-encrypted session
  store, `GitHubReleaseChannel`, `EcdsaSignatureVerifier`, `InstallerLauncher`,
  `JsonSettingsStore`, `AppDataPaths`, `Replica/SqliteReplica` (Microsoft.Data.Sqlite),
  `Sync/PostgrestRemoteTables`, `Sync/SupabaseChangeFeed`, `Assistant/SupabaseAssistantClient`, `Planning/TimerReminderScheduler`, the life goal
  picture adapters (`Planning/FilePictureFiles`, `Planning/SupabasePictureCloud`,
  `Planning/PictureShrinker`) and
  `Startup/` (GoalMaker's own value under Run, and finding Startup Profiles).
- `GoalMaker.App` (WPF, `net10.0-windows10.0.19041.0` for toasts, ADR 0009):
  `Composition/AppGraph` (composition root), `Shell/` (`AppShell`, what `App` puts up over the
  graph and the start-up smoke test builds the same way; Fluent main window, tray icon with the Today
  flyout, page provider, reminder toasts, the quick-add box and its global shortcut through NHotkey,
  the sidebar's pinned places and All places (`PlaceSidebar`, `AllPlacesItem`, ADR 0014), the
  sidebar's area and tag filters, the Today and Habits mini windows, the Projects page's new item
  window),
  `Controls/MarkdownView` (task notes), `Views/` and `ViewModels/` (CommunityToolkit.Mvvm; the
  Places page is `PlacesPage` over `PlacesHubViewModel`, whose tiles reuse the places' own rules),
  `Startup/` (launch switches, single instance), `Theming/` (the themes over WPF UI and the tray kit, and `TextScale`, which follows Windows' text size), `Localization/`
  (all copy in `Resources/Strings.xaml`), `Diagnostics/CrashLog`. Settings is the tray kit's settings
  page (`Views/SettingsPage` over `SettingsViewModel`): section cards, the section list, the jump and
  scroll hints and the rows are the kit's; `ViewModels/SettingsPageRules` asks the kit's rules the
  questions `contracts/vectors/settings.json` asks. A danger row asks before it acts with the kit's
  `TrayConfirmWindow` (Cancel focused): "Sign out anyway" through its `DangerRow`, Restore from
  `AppGraph` once the file is read. The page title and the cards take the theme's heading font and
  card corner (`SettingsStyles` keys, set by `Theming/ThemeApplier`). The typed-time and backend-address rules are `GoalMaker.Core` `Settings/SettingsFieldRules`.
- The tray kit is the shared `DotNetLib.Tray` package from `dotnetlib` (ADR 0017; the feed is in
  [docs/setup/local-development.md](docs/setup/local-development.md#windows)). It brings WPF UI and
  H.NotifyIcon. `App.xaml.cs` takes the single-instance lock, then `Theming/AppResources` merges
  the kit's dictionaries (WPF UI's themes and controls, the kit's Window style) and then GoalMaker's
  own. `Theming/ThemeApplier` lets the kit's `TrayThemeApplier` switch WPF UI's theme, follow
  Windows in System mode and set the `Tray.*` brushes from the theme's palettes
  (`Theming/TrayPalettes`, with the theme's highlight spot and title), then sets everything of GoalMaker's own. `Shell/TrayMenu` builds the
  menu with `TrayMenuBuilder`, the tray icon is the logo made into an `.ico` by the kit's
  `IconFile`, and `Shell/StartupFailure` uses the kit's `TrayMessageWindow`. `Shell/TrayIcon` (the
  Today flyout and the double click) and `Startup/SingleInstance` (a second launch hands over its
  switches) stay GoalMaker's own until the kit can do the same.

### Shared behavior (`contracts/`)

Rules that must match across Kotlin and C# live as vector files, one per rule, listed in
[contracts/README.md](contracts/README.md): versions and the update offer policy, release manifest
verification, the sync rules, the composer grammar, the lists and their filter, Plan tomorrow,
repeating tasks, reminder times, the archive, the light Markdown in notes, the bottom bar's want,
habit and goal lines, goals, habits, reviews and
their prompts, the activity log, the stats numbers, project boards, the calendar, and the Settings
page's jump navigation and hints (`settings.json`: section order, the 4+ rule, the current section,
jump timing, the hints and the Saved mark). The connector's
TypeScript rules run the planning ones too, so Claude and the apps agree.
`contracts/content/prompts.json` is shipped content rather than a vector file: the review prompt
library, validated by `tools/check_prompts.py`. Both test suites read the same files. `contracts/schemas/synced-tables.json`
describes every synced column once; both apps build their replica SQL and JSON mapping from it, and
`tools/check_synced_tables.py` keeps it equal to the replica and server schemas.

### Design tokens (`contracts/design/`, `fonts/`)

The four switchable themes (ADR 0008, [docs/design/spec.md](docs/design/spec.md)) live once in
`themes.json`; both apps load it at run time and `tools/check_design_tokens.py` holds every theme to
WCAG AA, the Settings highlight included (text and the title 4.5:1 on the tinted card). Windows sets
the tray kit's `Tray.Highlight*` brushes from each theme's `highlight` tokens. Android maps the roles onto Material 3 (`ui/theme/GoalMakerTheme`, `AppTheme`) and uses
the variable fonts from `fonts/`; Windows fills `GM.*` resources and WPF UI's keys
(`Theming/ThemeApplier`) and uses static faces cut by `tools/build_windows_fonts.py`.

### Replica schema (`replica/`)

One set of immutable SQLite migrations that both apps apply unchanged (ADR 0007): Android packages
them as assets at build time, Windows embeds them. Both record each file's SHA-256 exactly like
`tools/migrations.py`, which tests the chain in CI.

### Backend (`supabase/`)

`config.toml` for the local stack (ports 553xx so it can run beside other projects), immutable
migrations locked by checksum, pgTAP tests, isolated-migration fixtures, the sign-in code email
template. `tools/supabase_migrations.py` runs the full chain, pgTAP and isolated N-1 → N steps.

`functions/` holds the Edge Functions (TypeScript on Deno 2.1, pinned through npm):

- `connector/`: the Claude connector, a stateless MCP server (docs/connector.md). It resolves the
  secret in its URL to the owner and runs every call in one transaction as that owner.
- `assistant/`: the quick chat (M7, docs/assistant.md). The apps call it with the owner's own session
  (the gateway checks the JWT); it runs a tool loop over the connector's tools minus the ones that
  delete, with Gemini's free tier behind the `ChatProvider` interface in `_shared/assistant/`, each
  tool in a transaction as the owner, logged as the owner's change through the chat. Its limits are
  counted in `assistant_usage`; the `GEMINI_API_KEY` function secret holds the model key.
- `_shared/`: what the connector and the quick chat share: `owner.ts` (the owner-scoped
  transaction), `planner/` (data access that does what the apps' lists do), `tools/` and
  `prompts/`, `assistant/` (the chat's provider interface, Gemini and fake providers, prompt and
  tool loop), and `rules/`, the planning rules ported from Kotlin and C#, which run the same vectors
  in `contracts/vectors/`. `deno task test` runs them; `connector/endpoint_test.ts` and
  `assistant/endpoint_test.ts` drive the running functions on the local stack.

## Data flow

- **Sign-in:** the app asks Supabase Auth to email a code, verifies it, and stores the session
  (Android: private preferences through supabase-kt; Windows: a DPAPI file). A trigger creates the
  user's `profiles` row. Every table is owner-only through row security.
- **Updates:** release source (manifest and signature from `releases/latest/`) → signature check
  with the key built into the app → manifest validation → version policy (dev builds never update,
  pre-releases are never offered) → download → size and SHA-256 check → platform installer. The
  updater never touches user data. The first three steps also run on their own about once a day;
  the installer waits for the owner's Install. On the phone, a found update also posts one quiet
  notification per version and is downloaded and verified in the background on an unmetered
  network, so Install takes seconds.
- **Sync (docs/sync.md):** a local write stores the row and queues it in the outbox in one
  transaction, then asks for a sync (debounced 2 s). A run pushes the outbox in order (the server
  stamps `updated_at`), then pulls each table from its watermark minus 60 s in (updated_at, id)
  pages of 500 and merges: a pending local change wins, except against a tombstone. Runs also
  happen at sign-in, when the app comes to the front, on Realtime events and (re)joins, every
  5 minutes on Windows and every 15 minutes through WorkManager on Android. Offline, both retry on
  their own (15 s doubling to 5 min), and Android also queues a network-constrained worker.
- **Reminders ([docs/reminders.md](docs/reminders.md)):** each device resolves reminders from its
  replica and keeps one alarm (Android `AlarmManager`) or timer (the Windows tray app) armed for the
  next. When it goes off, or the device boots, wakes or changes its clock, the device shows what
  arrived since its last look and arms the next. Buttons write the reminder's state through the
  outbox; after every sync each device takes down notifications that went stale.
- **Sign-out:** push once more, then empty the replica; if changes can't be pushed, ask first.
- **Settings:** theme, quiet hours, the last reminder look, the last update check (on the phone
  also the notified update and Later), dev backend override and (Windows) window placement stay on
  the device.

## Capability modules

- **updating:** `ReleaseChannel` / `IReleaseChannel`, `SignatureVerifier` / `ISignatureVerifier`,
  `UpdateInstaller` / `IUpdateInstaller`, coordinated by `UpdateService`. Health shows in Settings →
  Updates (not configured, dev build, up to date, available, untrusted, failed). `UpdateService`
  keeps the update the last check found (`waiting` / `Waiting` with `WaitingChanged`); while one
  waits, the way to Settings wears an accent mark with a download arrow (the Settings item in the
  Windows sidebar, the gear in the phone's top bar) and Settings shows it as an accent row with the
  install button. A check that gets an answer and finds none clears it, a check that fails (offline,
  GitHub down) leaves it, and a new version starts without it.
  `AutoUpdateCheck` (application layer, a clock and a one-day interval injected) is the quiet
  check: the composition root starts it a little after launch (Windows: a `TimeProvider` timer, 30 s
  and then hourly; Android: a few seconds after each `ProcessLifecycleOwner` start) and it checks
  only when the last check that reached the channel is a day old, or when that check found an update
  this run has not shown yet. It never downloads or installs, logs failures without a problem entry,
  and never runs in a dev build or one without a channel. The last check (time and the version it
  found) sits in the settings store; Settings → Updates shows "Last checked". Check for updates goes
  through it too, so both are recorded. Android has no WorkManager job for the check itself: the
  waiting update lives in memory, so a background check in a process that is gone by the next
  start shows nothing the start check does not.
  On Android, `UpdateAlerts` (application layer, a clock injected) hears every check and decides
  the rest: one notification per version (`UpdateNotifier`, the App updates channel; the notified
  version sits in the settings store), taken down when a check finds none or the version is
  installed; Later (`UpdatePostponement`, three days or until a newer version, also in the settings
  store), which hides the mark and the notification; and the background download
  (`UpdateDownloads`, one-time WorkManager work on an unmetered network with the battery not low).
  `UpdateService.prefetch` writes the APK to the private cache through `UpdateFiles` and keeps it
  only when it matches the manifest's size and SHA-256; Install opens a matching file at once and
  otherwise downloads first. The gear's mark follows `UpdateAlerts.mark`; Settings keeps the row
  and Install while an update is put off.
- **syncing:** `Replica` / `IReplica` and `RemoteTables` / `IRemoteTables`, run by `SyncEngine` and
  scheduled by `SyncCoordinator`. Health shows under Today's title (synced at, syncing, offline
  with the number of waiting changes, or changes the server refused).
- **reminding:** `ReminderScheduler` / `IReminderScheduler` (the platform's one alarm or timer),
  run by `ReminderService` over `ReminderList` and the shared `ReminderSchedule` rules; the platform
  shows and takes down the notifications.

## Connections

- Supabase over HTTPS with the publishable key plus the user's session. Dev builds use the local
  stack over plain HTTP (Android: debug-only network security config), with a dev-only override in
  Settings → Developer. Each backend gets its own replica file, so switching never mixes rows.
- Sync talks to PostgREST directly with generic JSON rows: 408, 429 and 5xx mean "offline, try
  later"; a 401 renews the session once and tries again, and a second 401 or a renewal the server
  refuses ends the session (signed out, the replica and outbox kept, no retries); any other error
  marks that one row as refused and the run goes on. Realtime is a nudge
  only (Android keeps it open only while the app is on screen); its payloads are never applied.
- Sign-in failures map to plain states (wrong or expired code, too many requests, offline, other);
  the Supabase clients refresh sessions and retry with their own backoff.
- The Windows single-instance pipe accepts connections from the current user only.

## Delivery

- One `version.properties`; `-dev` for every local build (Android `versionNameSuffix`, Windows
  `GoalMakerReleaseBuild=false`), so the updater never mistakes a dev build for a release.
- Android: debug `com.goalmaker.app.debug` and release `com.goalmaker.app` can be installed side by
  side; the release key is described in [docs/setup/signing-and-releases.md](docs/setup/signing-and-releases.md).
- Windows: framework-dependent publish and a per-user Inno Setup installer with a stable AppId, an
  optional sign-in start (`--tray`), and a separate side-by-side "GoalMaker Dev" flavor.
- Release workflow: tag → tests → signed APK + installer → signed manifest → published GitHub
  Release.

## Known constraints

- Material 3 Expressive is alpha-only (ADR 0005) and needs compileSdk 37; targetSdk stays 36 until
  Android 17's behavior changes are reviewed.
- The Supabase free plan allows 50 MB per stored file and two active projects per organization.
- The default Supabase email service only delivers to the project team's own addresses and is
  rate-limited, which suits a single owner.
- Windows reminder toasts are heard only while GoalMaker runs (ADR 0009); quitting takes them
  down, and the installer offers to start GoalMaker in the tray at sign-in for that reason.
- WPF UI's navigation items don't expose an action to UI Automation; the accessibility pass in M6
  must cover them. Test scripts use the `--open` switch instead.
- The Android session sits in private app storage unencrypted; encrypting it with the Android
  Keystore is planned before v1.0.
