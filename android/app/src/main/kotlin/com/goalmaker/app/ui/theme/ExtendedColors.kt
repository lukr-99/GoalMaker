package com.goalmaker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic roles Material's color scheme doesn't name. Placeholder values until the design
 * questionnaire (roadmap, before M2); screens use the roles, never literal colors.
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val streak: Color,
    val onStreak: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = Color.Unspecified,
        onSuccess = Color.Unspecified,
        streak = Color.Unspecified,
        onStreak = Color.Unspecified,
    )
}
