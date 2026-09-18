# M0-07: Windows installer

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- Inno Setup per-user installer with a stable AppId, "Start GoalMaker in the tray when I sign in"
  checked by default, uninstall that keeps user data unless asked.
- `windows/installer/build-installer.ps1`: publish, compile, write SHA-256.

## Acceptance criteria
- The script produces an installer and checksum locally; a silent install and uninstall work.

## Result
- Framework-dependent (about 5 MB instead of 46 MB self-contained, under the 50 MB bucket limit); the installer checks for the .NET 10 Desktop Runtime.
- Verified: silent install, the `--tray` Run key, relaunch after a silent install, uninstall removes the app and the Run key and keeps user data.
