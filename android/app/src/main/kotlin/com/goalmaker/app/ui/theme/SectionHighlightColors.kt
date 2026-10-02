package com.goalmaker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.goalmaker.app.domain.design.HighlightTokens

/**
 * A Settings card's highlight in the current theme and mode (contracts/design/themes.json,
 * "highlight"): the card's fill at the peak, the inner ring, the soft glow, the left edge bar and
 * the title color.
 */
@Immutable
data class SectionHighlightColors(
    val tint: Color,
    val ring: Color,
    val glow: Color,
    val edge: Color,
    val title: Color,
) {
    companion object {
        /** The tokens over a card of color [surface]. */
        fun from(tokens: HighlightTokens, surface: Color): SectionHighlightColors {
            val spot = Color(tokens.spot)
            return SectionHighlightColors(
                tint = mix(surface, spot, tokens.tint),
                ring = Color(tokens.ring).copy(alpha = tokens.ringAlpha),
                glow = spot.copy(alpha = tokens.glowAlpha),
                edge = Color(tokens.edge),
                title = Color(tokens.title),
            )
        }

        // Mixed in sRGB like the contract's contrast check, not in Compose's perceptual space.
        private fun mix(base: Color, over: Color, amount: Float) = Color(
            red = base.red + (over.red - base.red) * amount,
            green = base.green + (over.green - base.green) * amount,
            blue = base.blue + (over.blue - base.blue) * amount,
        )
    }
}
