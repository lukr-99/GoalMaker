# M0-07: Windows installer

**Status:** todo · **Milestone:** M0

## Scope
- Inno Setup per-user installer with a stable AppId, "Start GoalMaker in the tray when I sign in"
  checked by default, uninstall that keeps user data unless asked.
- `windows/installer/build-installer.ps1`: publish, compile, write SHA-256.

## Acceptance criteria
- The script produces an installer and checksum locally; a silent install and uninstall work.
