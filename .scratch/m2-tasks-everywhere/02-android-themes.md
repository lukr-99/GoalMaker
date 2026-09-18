# M2-02: Android theme engine and appearance settings

**Status:** todo · **Milestone:** M2

## Scope
- Load `themes.json` from assets; build the Material 3 color scheme, typography (variable fonts
  with weight, width and italic settings) and shapes from the chosen theme and mode; expose the
  GoalMaker roles (`hero`, `accent`, areas) through a CompositionLocal.
- Settings → Appearance: theme cards with a preview, mode, pure black, reduce motion, completion
  sound. Stored in settings; the status bar and launcher splash follow the theme.

## Acceptance criteria
- A unit test builds every theme and mode from the real file.
- Switching theme or mode applies at once, without a restart.
