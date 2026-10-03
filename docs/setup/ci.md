# CI

Three workflows run in `.github/workflows/`:

- `ci.yml` (CI): the Supabase, Android and Windows checks, on pull requests and pushes to `main`.
- `codeprint.yml` (Repository baseline): the `validate` job, the repository-wide Python and
  PowerShell checks, on pull requests and pushes to `main`.
- `release.yml` (Release): the signed APK, the installer and the GitHub Release, on `v*` tags
  ([signing and releases](signing-and-releases.md)).

## Required checks

Branch protection on `main` requires four check names. They are the same as before the split below:

- Supabase migrations, row security and the connector
- Android build, unit tests and lint
- Windows format, build and tests
- validate

Each of them reports on every pull request, even when its part did not change. Do not add the
helper jobs ("What changed", "Supabase stack, ...", "Supabase isolated migration steps N") as
required checks: the Supabase check already fails when one of them fails, and they are skipped on a
pull request that does not touch Supabase.

## How CI is split

**What changed.** A cheap first job lists a pull request's files (`dorny/paths-filter`, through the
API, no checkout). A part runs unless every changed file is one it cannot read:

| Part | Skipped when every file is in |
| --- | --- |
| Supabase | `android/`, `windows/`, `docs/`, `fonts/`, root `*.md`, `tools/` (except `supabase_migrations.py` and `check_synced_tables.py`) |
| Android | `windows/`, `supabase/`, `docs/`, root `*.md`, `tools/`, `package.json`, `package-lock.json` |
| Windows | `android/`, `supabase/`, `docs/`, root `*.md`, `tools/`, `package.json`, `package-lock.json` |

`contracts/`, `replica/`, `version.properties`, the workflows and any new top-level folder run
everything. A push to `main` always runs everything. The `tools/` scripts are checked by `validate`.

The Android and Windows jobs always start and report. When their part did not change they run one
step that says so and pass. If the changes job itself fails, they check everything.

**Supabase** used to be one 12-minute job. The migration harness took 10 of those minutes: 21
`supabase db reset` runs at about 28 s each (each one recreates the database container), while
pgTAP itself takes about 2 s. It is now three jobs that run side by side:

- *Supabase stack, pgTAP, the connector and quick chat*: starts the stack without realtime,
  storage, the mail viewer and the dashboard helpers, runs pgTAP on the chain that
  `supabase start` just applied (`test --part chain --skip-reset`), the synced-tables check, and the
  connector and quick chat tests.
- *Supabase isolated migration steps 1 to 4*: each starts Postgres only (`supabase db start`) and
  runs every fourth N-1 -> N step (`test --part steps --shard K/N`). The shard number and count come
  from the matrix, so adding a value to `shard:` spreads the steps further.
- *Supabase migrations, row security and the connector*: the required check. It passes when both
  jobs passed, or when the changes job said Supabase did not change.

Locally `python tools/supabase_migrations.py test` still runs everything in one go.

**UI tests.** The Android job's `testDebugUnitTest` runs the Compose screen tests with the other
Robolectric tests. The Windows job's `dotnet test --solution` runs `GoalMaker.App.SmokeTests`, the
start-up smoke test, as its own test process: it builds the app as a dev build starts, over a
throwaway folder, shows the main window without activating it and opens every page, in about 5 s.
It needs a desktop session with a taskbar for the tray icon, which the `windows-latest` runners
have. The Maestro flows need an emulator and do not run in CI ([local
development](local-development.md#ui-tests)).

**Concurrency.** A new push to a pull request cancels its older CI and baseline runs. Each push to
`main` gets its own group and is never cancelled (see the pitfall "A merge to main cancelled the
one before it").

**Caching.** Gradle (`gradle/actions/setup-gradle`, read-only outside `main`), NuGet
(`setup-dotnet` with `cache: true`, in CI and in the release) and npm (`setup-node` with
`cache: npm`). The Supabase Docker images are pulled each time: pulling them from the registry is
about as fast as restoring multi-gigabyte `docker save` tarballs, and they would crowd the 10 GB
cache.

**Release.** Unchanged apart from the NuGet cache: it still checks that the tag matches
`version.properties`, that every secret is set, runs the unit tests, verifies the APK signature and
signs the manifest. A tag run can read caches saved on `main`, never a pull request's.

## Timings

Measured over the last 30 runs before the change (2026-10-01), median:

| Job | Before | Expected after |
| --- | --- | --- |
| Supabase (critical path) | 12.0 min | about 3.5 min (stack ~3 min, each shard ~3 min, check ~10 s) |
| Android | 4.7 min | the same; about 15 s when Android did not change |
| Windows | 3.7 min | the same; about 15 s when Windows did not change |
| CI wall clock | 12.1 min | about 5 min, set by Android |
| validate | 0.2 min, twice per pull request push | once per pull request push |
| Release | 6.3 min, set by the signed APK | the same |

The split uses more runner minutes in total (about 15 instead of 12 for Supabase), which costs
nothing on a public repository.
