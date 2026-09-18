# M2-02: Android theme engine and appearance settings

**Status:** done 2026-09-18 · **Milestone:** M2

## Scope
- Load `themes.json` from assets; build the Material 3 color scheme, typography (variable fonts
  with weight, width and italic settings) and shapes from the chosen theme and mode; expose the
  GoalMaker roles (`hero`, `accent`, areas) through a CompositionLocal.
- Settings → Appearance: theme cards with a preview, mode, pure black, reduce motion, completion
  sound. Stored in settings; the status bar and launcher splash follow the theme.

## Acceptance criteria
- A unit test builds every theme and mode from the real file.
- Switching theme or mode applies at once, without a restart.

## Result
- `domain/design` parses the tokens; `GoalMakerTheme` builds Material 3 colors, the variable fonts
  (weight, width, slant) and shapes, and `AppTheme` exposes the GoalMaker roles; Track uppercases
  headings in the app's locale. Settings > Appearance with theme cards drawn in each theme.
- Verified on the emulator: Track light, Track dark, Electric dark; switching applies at once.
- Left for M2-06: Material's checkbox can't take the theme's corner shape, so a GoalMaker checkbox
  arrives with the check-morph animation.
