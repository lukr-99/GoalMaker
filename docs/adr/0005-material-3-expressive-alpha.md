# ADR 0005: Use Material 3 Expressive from the alpha channel, pinned

The owner chose a bold, energetic look. Material 3 Expressive (spring motion scheme, flexible app
bars, button groups, new indicators) fits it, but as of September 2026 it is only in
`androidx.compose.material3` 1.5.0-alpha, behind `ExperimentalMaterial3ExpressiveApi`. We pin one
exact alpha version and keep all Expressive usage inside `ui/theme` and a few wrapper components, so
a breaking alpha or a need to fall back to stable 1.4 touches only those files. We upgrade the pin
deliberately, not through the Compose BOM.
