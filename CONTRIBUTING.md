# Contributing

## Local verification

Run what your change touches; CI runs all of it on every push to `main` and every pull request.

```powershell
# Repository baseline (docs, one type per file, migration names, links)
python tools/validate_repository.py --root .
powershell -ExecutionPolicy Bypass -File tools/test-powershell-syntax.ps1

# Supabase: start the local stack once, then run the migration harness
npm install
npx supabase start
python tools/supabase_migrations.py test

# Android
android\gradlew.bat -p android testDebugUnitTest assembleDebug lintDebug

# Windows
dotnet format windows\GoalMaker.slnx --verify-no-changes
dotnet build windows\GoalMaker.slnx
dotnet test --solution windows\GoalMaker.slnx
```

## CI with Kiln

On this PC, run CI locally in Kiln first. GitHub Actions stays the fallback and the check that
gates merging. Start the Kiln daemon or tray app if `http://127.0.0.1:7878/` does not answer, then:

```powershell
kiln run .kiln/workflows/codeprint.yml --repo .   # repository baseline (GitHub: "validate")
kiln run .kiln/workflows/ci.yml --repo .          # connector tests, Android, Windows core build
kiln run .kiln/workflows/ci.yml --job android --repo .   # one job only
```

Kiln jobs run in Linux Docker containers, so some GitHub jobs cannot run there:

| Job | Where it runs | Why |
| --- | --- | --- |
| validate, PowerShell syntax | Kiln and GitHub | |
| Supabase connector unit tests | Kiln and GitHub | In Kiln they are their own job, in GitHub a step of the stack job. |
| Android build, unit tests, lint | Kiln and GitHub | |
| Windows core format and build | Kiln only | A Linux stand-in for the Windows job: the non-UI projects build, the tests cannot run. |
| Supabase stack, pgTAP, end-to-end tests, isolated steps 1 to 4 | GitHub only | Needs Docker inside the job (Kiln plan R1, KLN-44). It must also not clash with the local stack on ports 553xx. |
| Windows format, build and tests (whole solution, WPF tests) | GitHub only | Needs a Windows runner (KLN-45) and the private dotnetlib feed token. |

The GitHub-only jobs sit in `.kiln/workflows/ci-github-only.yml`. It is opt-in and fails today, so
do not run it as part of the normal check. A change to Supabase or the Windows app still needs the
real check: run `python tools/supabase_migrations.py test` and `dotnet test --solution
windows\GoalMaker.slnx` on this PC, and wait for GitHub CI on the pull request. When Kiln gets the
missing feature, move the job into `ci.yml`.

## Migrations, columns and contracts

Adding a migration: write `supabase/migrations/NNNN_description.sql`, add
`supabase/migration-tests/NNNN_before.sql` and `NNNN_after.sql`, add pgTAP tests under
`supabase/tests/database/`, run the harness, then `python tools/supabase_migrations.py lock` and
commit the lock file with the migration. Never edit a locked migration.

Adding a column to a synced table: add it to `contracts/schemas/synced-tables.json` with its kind
and whether the server needs a value in it, to the Supabase migration and to the replica migration,
then give it a value wherever a row is created. `python tools/check_synced_tables.py --server` keeps
the three in step.

Changing a contract: edit the vector file in `contracts/vectors/`, then both implementations, in one
commit. `contracts/vectors/release-manifest.json` is regenerated with
`python tools/generate_manifest_vectors.py`.

Known traps, and what they cost: [docs/pitfalls.md](docs/pitfalls.md).

## Change shape

- Keep each commit to one coherent behavior or repository change.
- Use Conventional Commits: `type(optional-scope): imperative summary`.
- Keep required tests and documentation with the behavior they protect.
- Do not commit generated builds, secrets, signing files, local SDK paths, or device identifiers.

## Pull requests

State the outcome, verification, screenshots for UI work, data/migration impact, and rollback or
recovery for risky delivery changes.
