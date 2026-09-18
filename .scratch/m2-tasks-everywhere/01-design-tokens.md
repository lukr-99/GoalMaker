# M2-01: Design tokens, brand mark and fonts

**Status:** done 2026-09-18 · **Milestone:** M2

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

## Result
- `contracts/design/themes.json` and `tools/check_design_tokens.py` (in the CodePrint workflow); all
  four themes pass WCAG AA in light, dark and black, and the checker fails a broken pair.
- The Chart G from `tools/generate_app_icon.py` in the `.ico`, the Android adaptive icon and
  `docs/design/brand/goalmaker-icon.svg`; it reads at 32 px and sits inside Android's safe zone.
- `fonts/` holds the four OFL families from Google Fonts; Android packages them as assets and
  `tools/build_windows_fonts.py` cuts 12 reproducible static faces for WPF.
