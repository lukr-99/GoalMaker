# M0-02: Shared contracts

**Status:** todo · **Milestone:** M0

## Scope
- `contracts/schemas/release-manifest.schema.json`.
- Vector files: semantic version comparison, release manifest parsing and signature verification
  (ECDSA P-256, test key only).
- Kotlin and C# tests load the same files.

## Acceptance criteria
- Both test suites fail if a vector case is removed from either implementation or disagrees.
