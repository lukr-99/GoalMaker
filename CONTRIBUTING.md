# Contributing

## Local verification

Run what your change touches; CI runs all of it on every push to `main` and every pull request.

```powershell
# Repository baseline (docs, one type per file, migration names, links)
python tools/validate_repository.py --root .
powershell -File tools/test-powershell-syntax.ps1

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

Adding a migration: write `supabase/migrations/NNNN_description.sql`, add
`supabase/migration-tests/NNNN_before.sql` and `NNNN_after.sql`, add pgTAP tests under
`supabase/tests/database/`, run the harness, then `python tools/supabase_migrations.py lock` and
commit the lock file with the migration. Never edit a locked migration.

Changing a contract: edit the vector file in `contracts/vectors/`, then both implementations, in one
commit. `contracts/vectors/release-manifest.json` is regenerated with
`python tools/generate_manifest_vectors.py`.

## Change shape

- Keep each commit to one coherent behavior or repository change.
- Use Conventional Commits: `type(optional-scope): imperative summary`.
- Keep required tests and documentation with the behavior they protect.
- Do not commit generated builds, secrets, signing files, local SDK paths, or device identifiers.

## Pull requests

State the outcome, verification, screenshots for UI work, data/migration impact, and rollback or
recovery for risky delivery changes.
