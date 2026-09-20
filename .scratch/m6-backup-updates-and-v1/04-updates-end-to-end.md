# M6-04: Updates through the channel, end to end

**Status:** todo · **Milestone:** M6

## Scope
- The update seams and the signed manifest are M0-06 and were checked on Windows against the local
  stack. This issue proves the whole path on a real release channel for both apps (spec, stories 93
  and 94): a tag builds the artifacts, the release workflow publishes them and the signed manifest
  to the private bucket, and an installed older copy finds, verifies and installs the newer one with
  the owner's session.
- Android: the installer intent from a FileProvider, the "install unknown apps" permission the first
  time, and what a refused or cancelled install shows.
- Both: the manual download path when the updater cannot finish (story 94), and an update the owner
  postpones.
- What an update must never do: install an artifact whose hash or signature does not match, or one
  older than what is installed.

## Acceptance criteria
- A real 0.9.x to 1.0.0 update installed from the channel on a phone and on a PC, both still signed
  in afterwards, with the check, the download and the install seen in the logs.
- A manifest signed with the wrong key, a corrupted artifact and an older version are each refused,
  with the owner told plainly.
