# M0-08: CI and the release workflow

**Status:** todo · **Milestone:** M0

## Scope
- `ci.yml`: repository validation, Supabase (harness + pgTAP), Android (build, unit tests, lint),
  Windows (format, build, tests). Least privilege, timeouts, caches, uploaded reports.
- `release.yml` on `v*` tags: build the signed APK and installer, write and sign the manifest,
  upload to the `releases` bucket, draft a GitHub Release with checksums.

## Acceptance criteria
- CI is green on `main`. The release workflow fails clearly when a required secret is missing.
