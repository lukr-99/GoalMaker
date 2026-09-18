# GoalMaker architecture

## Context

One owner uses GoalMaker on an Android phone and a Windows PC. Supabase (Postgres, Auth, Storage,
Realtime, later Edge Functions) is the source of truth; each app keeps a SQLite replica with an
outbox, so it works offline and syncs when it can (ADR 0002, ADR 0007, [docs/sync.md](docs/sync.md)). Claude reaches the data through a connector in M3 (ADR 0003). Releases reach both
apps through a signed update channel in Supabase Storage (ADR 0004).

```text
  Android app  ──┐                           ┌── Claude apps (M3, connector)
  (Kotlin)       │  HTTPS, user session      │
                 ├──────────► Supabase ◄─────┘
  Windows app  ──┘   Auth · Postgres (RLS) · Realtime · Storage (releases) · Edge Functions (M3)
  (.NET/WPF)                    ▲
                                │ migrations, deploys (CLI)      release workflow (CI)
                         supabase/ folder  ◄─────────────────────  uploads + signed manifest
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
  `account/` (email, sign-in code), `settings/ThemeMode`, `sync/` (`SyncRules`, the synced-table
  catalog, outbox entries, cursors). Pure Kotlin.
- `application/`: ports and use cases: `auth/AuthGateway`, `update/UpdateService` with the
  `ReleaseChannel`, `SignatureVerifier` and `UpdateInstaller` seams, `settings/SettingsStore`,
  `sync/` (`Replica` and `RemoteTables` ports, `SyncEngine`, `SyncCoordinator`),
  `planning/TaskList`.
- `data/`: `SupabaseAuthGateway`, `SupabaseReleaseChannel`, `EcdsaSignatureVerifier`,
  `ApkInstallerLauncher` (FileProvider), `SharedPreferencesSettingsStore`, `replica/`
  (`SqliteReplica` on the bundled SQLite driver, `ReplicaMigrator`, `SqlScript`), `sync/`
  (`PostgrestRemoteTables` over Ktor, `SupabaseChangeFeed`, `SyncWorker` and
  `WorkManagerSyncScheduler`).
- `ui/`: `theme/` (Material 3 Expressive, semantic tokens, pinned alpha per ADR 0005), `signin/`,
  `today/`, `settings/`, `nav/` (Navigation 3 back stack).
- `composition/AppGraph` is the composition root, owned by `GoalMakerApplication`, which also hands
  WorkManager a worker factory wired to it.

### Windows (`windows/`)

- `GoalMaker.Core` (net10.0): the same domain and application code as Android's, in C#
  (`Versioning`, `Updates`, `Account`, `Auth`, `Settings`, `Backend`, `About`, `Sync`, `Planning`).
- `GoalMaker.Infrastructure` (net10.0-windows): `SupabaseAuthGateway`, a DPAPI-encrypted session
  store, `SupabaseReleaseChannel`, `EcdsaSignatureVerifier`, `InstallerLauncher`,
  `JsonSettingsStore`, `AppDataPaths`, `Replica/SqliteReplica` (Microsoft.Data.Sqlite),
  `Sync/PostgrestRemoteTables` and `Sync/SupabaseChangeFeed`.
- `GoalMaker.App` (WPF): `Composition/AppGraph` (composition root), `Shell/` (Fluent main window,
  tray icon, page provider), `Views/` and `ViewModels/` (CommunityToolkit.Mvvm), `Startup/`
  (launch switches, single instance), `Theming/` (brand accent over WPF UI themes), `Localization/`
  (all copy in `Resources/Strings.xaml`), `Diagnostics/CrashLog`.
- `dotnetlib` was evaluated and is not referenced yet (ADR 0006).

### Shared behavior (`contracts/`)

Rules that must match across Kotlin and C# live as vector files: semantic versions and the update
offer policy, release manifest verification, and the sync rules (merge, full resync, pull start,
timestamp form). Both test suites read the same files. `contracts/schemas/synced-tables.json`
describes every synced column once; both apps build their replica SQL and JSON mapping from it, and
`tools/check_synced_tables.py` keeps it equal to the replica and server schemas.

### Design tokens (`contracts/design/`, `fonts/`)

The four switchable themes (ADR 0008, [docs/design/spec.md](docs/design/spec.md)) live once in
`themes.json`; both apps load it at run time and `tools/check_design_tokens.py` holds every theme to
WCAG AA. Android maps the roles onto Material 3 (`ui/theme/GoalMakerTheme`, `AppTheme`) and uses
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

## Data flow

- **Sign-in:** the app asks Supabase Auth to email a code, verifies it, and stores the session
  (Android: private preferences through supabase-kt; Windows: a DPAPI file). A trigger creates the
  user's `profiles` row. Every table is owner-only through row security.
- **Updates:** release source (manifest and signature from `releases/latest/`) → signature check
  with the key built into the app → manifest validation → version policy (dev builds never update,
  pre-releases are never offered) → download → size and SHA-256 check → platform installer. The
  updater never touches user data.
- **Sync (docs/sync.md):** a local write stores the row and queues it in the outbox in one
  transaction, then asks for a sync (debounced 2 s). A run pushes the outbox in order (the server
  stamps `updated_at`), then pulls each table from its watermark minus 60 s in (updated_at, id)
  pages of 500 and merges: a pending local change wins, except against a tombstone. Runs also
  happen at sign-in, when the app comes to the front, on Realtime events and (re)joins, every
  5 minutes on Windows and every 15 minutes through WorkManager on Android. Offline, both retry on
  their own (15 s doubling to 5 min), and Android also queues a network-constrained worker.
- **Sign-out:** push once more, then empty the replica; if changes can't be pushed, ask first.
- **Settings:** theme, dev backend override and (Windows) window placement stay on the device.

## Capability modules

- **updating:** `ReleaseChannel` / `IReleaseChannel`, `SignatureVerifier` / `ISignatureVerifier`,
  `UpdateInstaller` / `IUpdateInstaller`, coordinated by `UpdateService`. Health shows in Settings →
  Updates (not configured, dev build, up to date, available, untrusted, failed).
- **syncing:** `Replica` / `IReplica` and `RemoteTables` / `IRemoteTables`, run by `SyncEngine` and
  scheduled by `SyncCoordinator`. Health shows under Today's title (synced at, syncing, offline
  with the number of waiting changes, or changes the server refused).

## Connections

- Supabase over HTTPS with the publishable key plus the user's session. Dev builds use the local
  stack over plain HTTP (Android: debug-only network security config), with a dev-only override in
  Settings → Developer. Each backend gets its own replica file, so switching never mixes rows.
- Sync talks to PostgREST directly with generic JSON rows: 401, 408, 429 and 5xx mean "offline, try
  later"; any other error marks that one row as refused and the run goes on. Realtime is a nudge
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
- Release workflow: tag → tests → signed APK + installer → signed manifest → `releases` bucket →
  draft GitHub Release.

## Known constraints

- Material 3 Expressive is alpha-only (ADR 0005) and needs compileSdk 37; targetSdk stays 36 until
  Android 17's behavior changes are reviewed.
- The Supabase free plan allows 50 MB per stored file and two active projects per organization.
- The default Supabase email service only delivers to the project team's own addresses and is
  rate-limited, which suits a single owner.
- WPF UI's navigation items don't expose an action to UI Automation; the accessibility pass in M6
  must cover them. Test scripts use the `--open` switch instead.
- The Android session sits in private app storage unencrypted; encrypting it with the Android
  Keystore is planned before v1.0.
