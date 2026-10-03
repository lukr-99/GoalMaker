package com.goalmaker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.io.File

/**
 * Compose tests' theme: the default theme in light with motion reduced, so nothing waits on an
 * animation, and text drawn at [fontScale] (2 is the largest system text size).
 */
@Composable
fun TestTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        GoalMakerTheme(TOKENS, APPEARANCE, content = content)
    }
}

private val TOKENS = DesignTokens.parse(File(System.getProperty("goalmaker.contracts")!!, "design/themes.json").readText())

private val APPEARANCE = Appearance.DEFAULT.copy(mode = ThemeMode.LIGHT, reduceMotion = ReduceMotion.ON)
