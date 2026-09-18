# M0-06: Update channel in both apps

**Status:** todo · **Milestone:** M0

## Scope
- Seams: release source (manifest + signature from the `releases` bucket with the user session) →
  signature check with the built-in public key → version policy → verified download (SHA-256) →
  installer launch.
- Android: FileProvider + package installer. Windows: run the downloaded installer, then exit.
- Settings: "Check for updates" and the manual download path.

## Acceptance criteria
- Unit tests cover every seam with fakes; contract vectors pass.
- A manifest signed with the wrong key or an artifact with the wrong hash is refused.
