# ADR 0008: Four switchable themes from one token file, Track by default

The design questionnaire (docs/design/questionnaire.md) was answered on a board that drew four
directions as the same Today screen, and the owner asked for all four as themes the user can
switch, with the sporty Track theme as the default. GoalMaker therefore defines its themes once in
`contracts/design/themes.json` (palettes for light, dark and pure black, typography, corner shapes,
the area colors, spacing per device, motion timings) and both apps load that file at run time,
like the synced-table contract. `tools/check_design_tokens.py` runs in CI and refuses a theme that
is incomplete or misses WCAG AA contrast in any mode. A theme changes colors, fonts, corners and
the look of big numbers; spacing stays per device (airy phone, compact Windows) and behavior never
changes. The four font families (Archivo, Plus Jakarta Sans, Space Grotesk, Outfit, all under the
SIL Open Font License) are bundled so the look works offline and matches across apps: Android uses
the variable fonts, and Windows uses static instances cut from them because WPF can't select
variable axes. The cost is about 2 MB of fonts per app and four themes to keep polished instead of
one; the token file and its checker keep that manageable.
