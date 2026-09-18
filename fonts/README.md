# Fonts

The four font families behind GoalMaker's themes ([ADR 0008](../docs/adr/0008-switchable-themes.md),
[design spec](../docs/design/spec.md)), unchanged from Google Fonts' repository
(`github.com/google/fonts`, `ofl/<family>/`, downloaded 2026-09-18). Each is licensed under the
SIL Open Font License 1.1; the license text sits next to the font and ships with both apps.

| Family | Theme | Files |
|---|---|---|
| Archivo | Track | `archivo/Archivo[wdth,wght].ttf`, `archivo/Archivo-Italic[wdth,wght].ttf` |
| Plus Jakarta Sans | Electric | `plusjakartasans/PlusJakartaSans[wght].ttf` |
| Space Grotesk | Night | `spacegrotesk/SpaceGrotesk[wght].ttf` |
| Outfit | Sunrise | `outfit/Outfit[wght].ttf` |

- **Android** packages these variable fonts as assets (`app/build.gradle.kts`, `sharedAssets`) and
  sets weight, width and italic per theme.
- **Windows** can't select variable axes, so `tools/build_windows_fonts.py` (needs `fonttools`) cuts
  the static faces each theme uses into `windows/src/GoalMaker.App/Assets/Fonts/`. Rerun it after
  changing a font or a theme's typography; the output is committed and reproducible.

Fonts are never loaded from the network.
