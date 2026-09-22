# ADR 0010: Updates through public GitHub Releases, with the same signed manifest

ADR 0004 put updates in a private Supabase Storage bucket only because a private repository can't
serve release files to an installed app. The repository is now public, so the apps read the latest
published GitHub Release instead: `manifest.json` and `manifest.sig` from
`<repository>/releases/latest/download/`, and a manifest artifact `X.Y.Z/<file>` from
`<repository>/releases/download/vX.Y.Z/<file>` (pinned by `contracts/vectors/release-channel.json`).
Nothing about trust changes: the ECDSA P-256 signature over the manifest's exact bytes, the version
policy and each artifact's size and SHA-256 are still what make an update installable, and GitHub is
just where the bytes sit. What changes is that checking needs no sign-in, a file is no longer capped
at 50 MB, the manual download (story 94) is the same page the updater reads, and a copy of the
repository updates from its own releases because the release workflow builds the address from the
repository it runs in (`GOALMAKER_UPDATE_URL`). Releases are published, not drafted, since a draft is
invisible to the apps. The workflow keeps uploading to the old bucket until every installed copy
runs a version that reads GitHub; then the bucket upload, the Storage secret and migration 0002's
bucket can go. Supersedes the "where" of ADR 0004; its manifest and signing still stand.
