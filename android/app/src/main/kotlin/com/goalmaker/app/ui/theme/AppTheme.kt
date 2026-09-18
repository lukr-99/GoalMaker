package com.goalmaker.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.goalmaker.app.domain.design.AreaColor
import com.goalmaker.app.domain.design.Density
import com.goalmaker.app.domain.design.MotionTokens

internal val LocalGoalMakerColors = staticCompositionLocalOf<GoalMakerColors> { error("No GoalMakerTheme") }
internal val LocalGoalMakerType = staticCompositionLocalOf<GoalMakerTypeStyles> { error("No GoalMakerTheme") }
internal val LocalGoalMakerShapes = staticCompositionLocalOf<GoalMakerShapes> { error("No GoalMakerTheme") }
internal val LocalDensityTokens = staticCompositionLocalOf<Density> { error("No GoalMakerTheme") }
internal val LocalMotionTokens = staticCompositionLocalOf<MotionTokens> { error("No GoalMakerTheme") }
internal val LocalAreaColors = staticCompositionLocalOf<List<AreaColor>> { emptyList() }
internal val LocalReduceMotion = staticCompositionLocalOf { false }
internal val LocalCompletionSound = staticCompositionLocalOf { false }

/**
 * GoalMaker's theme values beside MaterialTheme's, the way screens read them:
 * `AppTheme.colors.hero`, `AppTheme.type.number`, `AppTheme.shapes.card`, `AppTheme.reduceMotion`.
 */
object AppTheme {
    val colors: GoalMakerColors
        @Composable @ReadOnlyComposable
        get() = LocalGoalMakerColors.current

    val type: GoalMakerTypeStyles
        @Composable @ReadOnlyComposable
        get() = LocalGoalMakerType.current

    val shapes: GoalMakerShapes
        @Composable @ReadOnlyComposable
        get() = LocalGoalMakerShapes.current

    val density: Density
        @Composable @ReadOnlyComposable
        get() = LocalDensityTokens.current

    val motion: MotionTokens
        @Composable @ReadOnlyComposable
        get() = LocalMotionTokens.current

    val areaColors: List<AreaColor>
        @Composable @ReadOnlyComposable
        get() = LocalAreaColors.current

    /** A screen or section title as the theme sets it (Track uppercases), in the app's locale. */
    @Composable
    @ReadOnlyComposable
    fun headline(text: String): String = type.headline(text, LocalConfiguration.current.locales[0])

    /** Whether completing something plays the soft tick (Settings > Appearance). */
    val completionSound: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalCompletionSound.current

    /** True when animations should become short fades (the system setting or the in-app switch). */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReduceMotion.current
}
