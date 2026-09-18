package com.goalmaker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.goalmaker.app.domain.design.AreaColor
import com.goalmaker.app.domain.design.Palette

/**
 * The current theme's color roles (contracts/design/themes.json). Screens use these roles, never
 * literal colors; Material components get the same roles through the color scheme.
 */
@Immutable
data class GoalMakerColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    /** An area chip's fill in this mode. */
    fun areaContainer(area: AreaColor): Color = Color(if (isDark) area.dark.container else area.light.container)

    /** An area chip's text in this mode. */
    fun areaContent(area: AreaColor): Color = Color(if (isDark) area.dark.content else area.light.content)

    companion object {
        fun from(palette: Palette, isDark: Boolean) = GoalMakerColors(
            background = Color(palette.background),
            surface = Color(palette.surface),
            surfaceVariant = Color(palette.surfaceVariant),
            text = Color(palette.text),
            textMuted = Color(palette.textMuted),
            outline = Color(palette.outline),
            primary = Color(palette.primary),
            onPrimary = Color(palette.onPrimary),
            accent = Color(palette.accent),
            onAccent = Color(palette.onAccent),
            hero = Color(palette.hero),
            onHero = Color(palette.onHero),
            heroAccent = Color(palette.heroAccent),
            danger = Color(palette.danger),
            isDark = isDark,
        )
    }
}
