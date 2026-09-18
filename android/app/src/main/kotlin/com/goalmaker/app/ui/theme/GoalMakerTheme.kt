package com.goalmaker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.goalmaker.app.domain.settings.ThemeMode

// Bold and energetic (grilling Q26): a saturated violet primary with a coral accent. The neutral
// surfaces are independent of the accents, as CodePrint's theming rule requires. Final values come
// from the design questionnaire; only this file changes then.
private val lightScheme = lightColorScheme(
    primary = Color(0xFF5B2EE6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFFE5533A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD3),
    onSecondaryContainer = Color(0xFF3E0600),
    tertiary = Color(0xFF00897B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF7FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFBF7FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454F),
    surfaceContainer = Color(0xFFF1ECF6),
    surfaceContainerHigh = Color(0xFFEBE6F0),
    outline = Color(0xFF79757F),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF30009B),
    primaryContainer = Color(0xFF4600D8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB4A5),
    onSecondary = Color(0xFF650F00),
    secondaryContainer = Color(0xFF8C1D08),
    onSecondaryContainer = Color(0xFFFFDAD3),
    tertiary = Color(0xFF6FD9C8),
    onTertiary = Color(0xFF003731),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF48454F),
    onSurfaceVariant = Color(0xFFC9C4D0),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    outline = Color(0xFF938F99),
)

private val lightExtended = ExtendedColors(
    success = Color(0xFF1B8A3A),
    onSuccess = Color(0xFFFFFFFF),
    streak = Color(0xFFFF8A00),
    onStreak = Color(0xFF2B1600),
)

private val darkExtended = ExtendedColors(
    success = Color(0xFF7ADB8F),
    onSuccess = Color(0xFF00391A),
    streak = Color(0xFFFFB866),
    onStreak = Color(0xFF462A00),
)

/** The app theme: Material 3 Expressive with the expressive motion scheme (ADR 0005). */
@Composable
fun GoalMakerTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
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
    CompositionLocalProvider(LocalExtendedColors provides if (dark) darkExtended else lightExtended) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            motionScheme = MotionScheme.expressive(),
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
