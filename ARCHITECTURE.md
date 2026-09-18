# GoalMaker architecture

## Context

One owner uses GoalMaker on an Android phone and a Windows PC. Supabase (Postgres, Auth, Storage,
later Realtime and Edge Functions) is the source of truth; each app will keep a replica for offline
use (ADR 0002). Claude reaches the data through a connector in M3 (ADR 0003). Releases reach both
apps through a signed update channel in Supabase Storage (ADR 0004).

```text
  Android app  ──┐                           ┌── Claude apps (M3, connector)
  (Kotlin)       │  HTTPS, user session      │
                 ├──────────► Supabase ◄─────┘
  Windows app  ──┘   Auth · Postgres (RLS) · Storage (releases) · Edge Functions (M3)
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
  `account/` (email, sign-in code), `settings/ThemeMode`. Pure Kotlin.
- `application/`: ports and use cases: `auth/AuthGateway`, `update/UpdateService` with the
  `ReleaseChannel`, `SignatureVerifier` and `UpdateInstaller` seams, `settings/SettingsStore`.
- `data/`: `SupabaseAuthGateway`, `SupabaseReleaseChannel`, `EcdsaSignatureVerifier`,
  `ApkInstallerLauncher` (FileProvider), `SharedPreferencesSettingsStore`.
- `ui/`: `theme/` (Material 3 Expressive, semantic tokens, pinned alpha per ADR 0005), `signin/`,
  `home/`, `settings/`, `nav/` (Navigation 3 back stack).
- `composition/AppGraph` is the composition root, owned by `GoalMakerApplication`.

### Windows (`windows/`)

- `GoalMaker.Core` (net10.0): the same domain and application code as Android's, in C#
  (`Versioning`, `Updates`, `Account`, `Auth`, `Settings`, `Backend`, `About`).
- `GoalMaker.Infrastructure` (net10.0-windows): `SupabaseAuthGateway`, a DPAPI-encrypted session
  store, `SupabaseReleaseChannel`, `EcdsaSignatureVerifier`, `InstallerLauncher`,
  `JsonSettingsStore`, `AppDataPaths`.
- `GoalMaker.App` (WPF): `Composition/AppGraph` (composition root), `Shell/` (Fluent main window,
  tray icon, page provider), `Views/` and `ViewModels/` (CommunityToolkit.Mvvm), `Startup/`
  (launch switches, single instance), `Theming/` (brand accent over WPF UI themes), `Localization/`
  (all copy in `Resources/Strings.xaml`), `Diagnostics/CrashLog`.
- `dotnetlib` was evaluated and is not referenced yet (ADR 0006).

### Shared behavior (`contracts/`)

Rules that must match across Kotlin and C# live as vector files: semantic versions and the update
offer policy, and release manifest verification. Both test suites read the same files.

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
- **Settings:** theme, dev backend override and (Windows) window placement stay on the device.

## Capability modules

- **updating:** `ReleaseChannel` / `IReleaseChannel`, `SignatureVerifier` / `ISignatureVerifier`,
  `UpdateInstaller` / `IUpdateInstaller`, coordinated by `UpdateService`. Health shows in Settings →
  Updates (not configured, dev build, up to date, available, untrusted, failed).

## Connections

- Supabase over HTTPS with the publishable key plus the user's session. Dev builds use the local
  stack over plain HTTP (Android: debug-only network security config), with a dev-only override in
  Settings → Developer.
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
