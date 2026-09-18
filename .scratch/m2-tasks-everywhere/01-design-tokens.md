# M2-01: Design tokens, brand mark and fonts

**Status:** doing · **Milestone:** M2

## Scope
- `contracts/design/themes.json` with the four themes (light, dark, black), typography, shapes,
  the 12 area colors, density and motion; `tools/check_design_tokens.py` in CI (ADR 0008).
- The G-with-trend-arrow mark in `tools/generate_app_icon.py`, written to the Windows `.ico`, the
  Android adaptive icon and `docs/design/brand/`.
- The four font families (OFL) in the repository with their license texts: variable files for
  Android, static instances for Windows cut from them by a script.

## Acceptance criteria
- The checker passes and fails on a deliberately broken pair.
- Both launchers and the installer show the new mark; it reads at 16 px in the tray.
