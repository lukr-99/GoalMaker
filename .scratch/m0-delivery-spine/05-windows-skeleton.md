# M0-05: Windows app skeleton

**Status:** done 2026-09-18 · **Milestone:** M0

## Scope
- Solution in `windows/`: `GoalMaker.Core`, `GoalMaker.App` (WPF UI shell), tests.
- Tray icon (H.NotifyIcon), single instance, `--tray` switch.
- Semantic tokens with light, dark and system modes.
- Sign-in with an emailed 6-digit code (Supabase C# client); session stored with DPAPI.
- `-dev` version for local builds; backend environment like Android.

## Acceptance criteria
- `dotnet format --verify-no-changes`, build and tests pass.
- The app starts, signs in against the local stack, and minimizes to the tray.

## Result
- Verified: sign-in against the local stack, the session survives a restart, a second launch hands `--open settings` to the running app, `--no-activate` leaves the focus alone, the window reopens where it was.
- WPF UI navigation items don't expose an action to UI Automation; the M6 accessibility pass must cover that.
