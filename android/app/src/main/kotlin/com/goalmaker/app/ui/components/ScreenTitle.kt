package com.goalmaker.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A screen's headline in its top bar, as the theme writes headlines and in the theme's accent, so each
 * theme's second color leads every screen (design spec, color roles).
 */
@Composable
fun ScreenTitle(text: String) {
    Text(AppTheme.headline(text), color = AppTheme.colors.accent)
}
