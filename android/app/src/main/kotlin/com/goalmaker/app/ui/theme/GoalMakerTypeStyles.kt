package com.goalmaker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import java.util.Locale

/**
 * The theme's own text styles beyond Material's scale: [heading] for screen titles and heroes,
 * [number] for big numbers (tabular figures). Compose has no text-transform, so themes with
 * uppercase headings (Track) go through [headline].
 */
@Immutable
data class GoalMakerTypeStyles(
    val heading: TextStyle,
    val number: TextStyle,
    val headingUppercase: Boolean,
) {
    /** A title as this theme sets it, uppercased by [locale]'s rules when the theme shouts. */
    fun headline(text: String, locale: Locale): String = if (headingUppercase) text.uppercase(locale) else text
}
