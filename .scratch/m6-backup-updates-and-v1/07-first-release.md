# M6-07: The owner's setup and the first release

**Status:** todo · **Milestone:** M6

## Scope
- The one-time setup the roadmap has carried since M0, following `docs/setup/`: create the cloud
  Supabase project and push the schema, set the function secrets, create the Android release key and
  the manifest signing key with `android/tools/setup-signing.ps1` and the release tools, commit the
  manifest public key, and protect `main`.
- The release itself: version 1.0.0 in `version.properties`, the tag, the workflow that builds the
  signed APK and the installer, publishes them and the signed manifest to the `releases` bucket, and
  drafts the GitHub Release.
- Install both from the release artifacts on a clean phone and a clean PC, sign in to the cloud
  project, and check that a task made on one turns up on the other.
- README, the setup guides and the roadmap say v1.0 is out and what is in it.

## Acceptance criteria
- Both apps installed from the published artifacts, signed in to the cloud project, syncing.
- The manifest in the bucket verifies with the committed public key, and an older build offered the
  update (M6-04).
- Nothing secret is in the repository; the keys are backed up where the setup guide says.
