# M0-06: Update channel in both apps

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- Seams: release source (manifest + signature from the `releases` bucket with the user session) →
  signature check with the built-in public key → version policy → verified download (SHA-256) →
  installer launch.
- Android: FileProvider + package installer. Windows: run the downloaded installer, then exit.
- Settings: "Check for updates" and the manual download path.

## Acceptance criteria
- Unit tests cover every seam with fakes; contract vectors pass.
- A manifest signed with the wrong key or an artifact with the wrong hash is refused.

## Result
- Verified end to end on Windows against the local stack: a 0.1.0 release build found a signed 0.1.1 manifest, downloaded and verified the installer, ran it, and came back as 0.1.1, still signed in.
- `tools/publish_release.py` writes, signs and uploads the manifest; the release workflow uses it.

## Follow-up
- The Android install step (package installer) is covered by unit tests; run it on the phone with the first real release.
