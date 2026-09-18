package com.goalmaker.app.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.design.ThemeDefinition
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode

/**
 * The app theme (ADR 0008): the chosen theme from contracts/design/themes.json in light, dark or
 * pure black, as Material 3 Expressive colors, type and shapes, plus GoalMaker's own roles in
 * [AppTheme]. Switching applies at once.
 */
@Composable
fun GoalMakerTheme(tokens: DesignTokens, appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val theme = tokens.theme(appearance.themeId)
    val palette = when {
        !dark -> theme.light
        appearance.pureBlack -> theme.black
        else -> theme.dark
    }
    val context = LocalContext.current
    val colors = remember(palette, dark) { GoalMakerColors.from(palette, dark) }
    val fonts = remember(context) { ThemeFonts(context.assets) }
    val type = remember(theme) { typography(theme, fonts) }
    val systemReducesMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val reduceMotion = when (appearance.reduceMotion) {
        ReduceMotion.SYSTEM -> systemReducesMotion
        ReduceMotion.ON -> true
        ReduceMotion.OFF -> false
    }

    // Edge-to-edge bars follow the app's theme, not the system's, so their icons stay readable.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalGoalMakerColors provides colors,
        LocalGoalMakerType provides type.second,
        LocalGoalMakerShapes provides GoalMakerShapes.from(theme.shapes),
        LocalDensityTokens provides tokens.density,
        LocalMotionTokens provides tokens.motion,
        LocalAreaColors provides tokens.areaColors,
        LocalReduceMotion provides reduceMotion,
        LocalCompletionSound provides appearance.completionSound,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme(colors),
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = type.first,
            shapes = shapes(theme),
            content = content,
        )
    }
}

/** Material's color slots filled from the theme's roles, so built-in components match. */
private fun colorScheme(c: GoalMakerColors): ColorScheme {
    val onDanger = if (c.danger.luminance() > 0.4f) Color.Black else Color.White
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.surfaceVariant,
        onPrimaryContainer = c.text,
        inversePrimary = c.accent,
        secondary = c.accent,
        onSecondary = c.onAccent,
        secondaryContainer = c.surfaceVariant,
        onSecondaryContainer = c.text,
        tertiary = c.hero,
        onTertiary = c.onHero,
        tertiaryContainer = c.surfaceVariant,
        onTertiaryContainer = c.text,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.textMuted,
        surfaceTint = c.primary,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        error = c.danger,
        onError = onDanger,
        errorContainer = c.surfaceVariant,
        onErrorContainer = c.danger,
        outline = c.outline,
        // Dividers and outlined buttons: visible on every surface, quieter than outline.
        outlineVariant = lerp(c.surfaceVariant, c.outline, 0.5f),
        surfaceBright = c.surface,
        surfaceDim = c.background,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceVariant,
        surfaceContainerHighest = c.surfaceVariant,
    )
}

/** Material's type scale in the theme's fonts, and GoalMaker's heading and number styles. */
private fun typography(theme: ThemeDefinition, fonts: ThemeFonts): Pair<Typography, GoalMakerTypeStyles> {
    val heading = theme.typography.heading
    val number = theme.typography.number
    val body = theme.typography.body
    val headingFamily = fonts.family(heading)
    val bodyFamily = fonts.family(body)
    val headingStyle: (TextStyle) -> TextStyle = { style ->
        style.copy(
            fontFamily = headingFamily,
            fontWeight = FontWeight(heading.weight),
            fontStyle = if (heading.italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = heading.tracking.em,
        )
    }
    val bodyStyle: (TextStyle, Int) -> TextStyle = { style, weight ->
        style.copy(fontFamily = bodyFamily, fontWeight = FontWeight(weight))
    }
    val defaults = Typography()
    val material = Typography(
        displayLarge = headingStyle(defaults.displayLarge),
        displayMedium = headingStyle(defaults.displayMedium),
        displaySmall = headingStyle(defaults.displaySmall),
        headlineLarge = headingStyle(defaults.headlineLarge),
        headlineMedium = headingStyle(defaults.headlineMedium),
        headlineSmall = headingStyle(defaults.headlineSmall),
        titleLarge = bodyStyle(defaults.titleLarge, body.strongWeight),
        titleMedium = bodyStyle(defaults.titleMedium, body.strongWeight),
        titleSmall = bodyStyle(defaults.titleSmall, body.strongWeight),
        bodyLarge = bodyStyle(defaults.bodyLarge, body.weight),
        bodyMedium = bodyStyle(defaults.bodyMedium, body.weight),
        bodySmall = bodyStyle(defaults.bodySmall, body.weight),
        labelLarge = bodyStyle(defaults.labelLarge, body.strongWeight),
        labelMedium = bodyStyle(defaults.labelMedium, body.strongWeight),
        labelSmall = bodyStyle(defaults.labelSmall, body.strongWeight),
    )
    val numberFamily: FontFamily = fonts.family(number)
    val own = GoalMakerTypeStyles(
        heading = headingStyle(defaults.displaySmall),
        number = TextStyle(
            fontFamily = numberFamily,
            fontWeight = FontWeight(number.weight),
            fontStyle = if (number.italic) FontStyle.Italic else FontStyle.Normal,
            fontFeatureSettings = "tnum",
        ),
        headingUppercase = heading.uppercase,
    )
    return material to own
}

/** Material's shape scale from the theme's corners. */
private fun shapes(theme: ThemeDefinition) = Shapes(
    extraSmall = GoalMakerShapes.corner(minOf(theme.shapes.checkbox, 8)),
    small = GoalMakerShapes.corner(theme.shapes.row),
    medium = GoalMakerShapes.corner(theme.shapes.row),
    large = GoalMakerShapes.corner(theme.shapes.card),
    extraLarge = GoalMakerShapes.corner(theme.shapes.card),
)
