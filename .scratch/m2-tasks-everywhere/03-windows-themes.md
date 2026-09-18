# M2-03: Windows theme engine and appearance settings

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- Load `themes.json` (embedded); fill a resource dictionary of GoalMaker brushes, fonts and corner
  radii, and WPF UI's accent and theme from the chosen theme and mode; pure black.
- Settings → Appearance as on Android; compact density.

## Acceptance criteria
- A unit test maps every theme and mode from the real file.
- Switching applies at once, including open windows.

## Result
- `ThemeApplier` fills GM.* brushes, fonts, corners and spacing from the tokens, plus the WPF UI keys
  its controls read directly; pages, rows, the composer and titles (`HeadlineText`) use GM.* keys.
  The window drops Mica so each theme's background shows. Settings > Appearance matches Android,
  and theme cards are selectable by keyboard and UI Automation.
- Verified: Track dark and Sunrise light, persisted in settings.json; 91 tests pass.
- Known limit: WPF UI's own controls (checkbox marks, navigation items, toggles) keep its neutral
  text and fills with the theme's accent, because they read per-control brush keys baked when WPF
  UI loads its theme.
