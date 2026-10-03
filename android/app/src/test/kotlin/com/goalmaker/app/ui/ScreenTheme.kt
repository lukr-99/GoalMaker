package com.goalmaker.app.ui

import androidx.compose.runtime.Composable
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.io.File

/**
 * The app's theme the way a screen test wants it: the shared tokens (contracts/design/themes.json),
 * the default theme in light, and motion reduced, so a ticked row leaves and a button settles at once
 * instead of after an animation.
 */
@Composable
fun ScreenTheme(content: @Composable () -> Unit) {
    GoalMakerTheme(TOKENS, Appearance.DEFAULT.copy(mode = ThemeMode.LIGHT, reduceMotion = ReduceMotion.ON), content = content)
}

private val TOKENS: DesignTokens by lazy {
    DesignTokens.parse(File(System.getProperty("goalmaker.contracts")!!, "design/themes.json").readText())
}
