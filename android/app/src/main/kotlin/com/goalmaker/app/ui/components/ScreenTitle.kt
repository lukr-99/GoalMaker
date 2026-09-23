package com.goalmaker.app.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A screen's headline in its top bar, as the theme writes headlines and in the theme's accent, so each
 * theme's second color leads every screen (design spec, color roles).
 *
 * It keeps to one line and shrinks to fit: a collapsed bar full of actions leaves the headline a
 * narrow gap, and a headline allowed to wrap there spilled out of the bar a letter at a time.
 */
@Composable
fun ScreenTitle(text: String) {
    Text(
        AppTheme.headline(text),
        color = AppTheme.colors.accent,
        autoSize = TextAutoSize.StepBased(minFontSize = SMALLEST, maxFontSize = LocalTextStyle.current.fontSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// How far a headline shrinks before it is cut short instead.
private val SMALLEST = 14.sp
