# M0-02: Shared contracts

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- `contracts/schemas/release-manifest.schema.json`.
- Vector files: semantic version comparison, release manifest parsing and signature verification
  (ECDSA P-256, test key only).
- Kotlin and C# tests load the same files.

## Acceptance criteria
- Both test suites fail if a vector case is removed from either implementation or disagrees.

## Result
- Two vector files, 15 semantic-version parse cases (including a trailing newline and a non-ASCII digit) and 17 manifest cases; Kotlin and C# pass all of them. Gradle treats `contracts/` as a test input so vector changes rerun the tests.
