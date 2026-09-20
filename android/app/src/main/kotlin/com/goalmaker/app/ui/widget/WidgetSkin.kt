package com.goalmaker.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import com.goalmaker.app.GoalMakerApplication
import com.goalmaker.app.domain.settings.ThemeMode

/**
 * The colours a widget draws in: the theme the owner chose, in the mode the system is in, so a widget
 * matches the app rather than the launcher (docs/design/spec.md).
 */
data class WidgetSkin(
    val background: Int,
    val surface: Int,
    val text: Int,
    val textMuted: Int,
    val accent: Int,
) {
    companion object {
        fun of(context: Context): WidgetSkin {
            val graph = (context.applicationContext as GoalMakerApplication).graph
            val appearance = graph.settings.appearance.value
            val theme = graph.design.theme(appearance.themeId)
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (appearance.mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDark
            }
            val palette = when {
                !dark -> theme.light
                appearance.pureBlack -> theme.black
                else -> theme.dark
            }
            return WidgetSkin(
                background = palette.background,
                surface = palette.surface,
                text = palette.text,
                textMuted = palette.textMuted,
                accent = palette.accent,
            )
        }
    }
}
