# M0-08: CI and the release workflow

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- `ci.yml`: repository validation, Supabase (harness + pgTAP), Android (build, unit tests, lint),
  Windows (format, build, tests). Least privilege, timeouts, caches, uploaded reports.
- `release.yml` on `v*` tags: build the signed APK and installer, write and sign the manifest,
  upload to the `releases` bucket, draft a GitHub Release with checksums.

## Acceptance criteria
- CI is green on `main`. The release workflow fails clearly when a required secret is missing.

## Result
- `ci.yml` (Supabase, Android, Windows) and `release.yml` are written and pass actionlint; `codeprint.yml` validates the repository.
- Pushed and watched. All three CI jobs (Supabase migrations and row security, Android build, unit
  tests and lint, Windows format, build and tests) are green on `main` at `2aa9276`.

## Left
- Protecting `main` with these three jobs as required checks stays with the owner's one-time setup
  (roadmap, M0), because it needs repository admin rights.
