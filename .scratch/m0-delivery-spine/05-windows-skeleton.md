# M0-05: Windows app skeleton

**Status:** todo · **Milestone:** M0

## Scope
- Solution in `windows/`: `GoalMaker.Core`, `GoalMaker.App` (WPF UI shell), tests.
- Tray icon (H.NotifyIcon), single instance, `--tray` switch.
- Semantic tokens with light, dark and system modes.
- Sign-in with an emailed 6-digit code (Supabase C# client); session stored with DPAPI.
- `-dev` version for local builds; backend environment like Android.

## Acceptance criteria
- `dotnet format --verify-no-changes`, build and tests pass.
- The app starts, signs in against the local stack, and minimizes to the tray.
