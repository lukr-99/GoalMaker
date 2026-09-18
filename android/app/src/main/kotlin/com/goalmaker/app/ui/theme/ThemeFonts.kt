package com.goalmaker.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.goalmaker.app.domain.design.BodyStyle
import com.goalmaker.app.domain.design.TypeStyle

/**
 * Builds font families from the variable fonts the build packages as assets (fonts/README.md). One
 * variable file serves every weight and width, so each style asks for exactly what the theme names.
 */
class ThemeFonts(private val assets: AssetManager) {
    /** A heading or number style: one face, at the theme's weight, width and slant. */
    fun family(style: TypeStyle): FontFamily = FontFamily(font(style.family, style.weight, style.width, style.italic))

    /** The reading font at the weights Material's components and the theme ask for. */
    fun family(style: BodyStyle): FontFamily = FontFamily(
        (setOf(style.weight, style.strongWeight) + BODY_WEIGHTS).sorted().map { weight ->
            font(style.family, weight, style.width, italic = false)
        },
    )

    private fun font(family: String, weight: Int, width: Float, italic: Boolean): Font {
        val files = FILES[family] ?: error("No bundled font named $family")
        val path = if (italic) files.second ?: error("$family has no italic") else files.first
        return Font(
            path = path,
            assetManager = assets,
            weight = FontWeight(weight),
            style = if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
        )
    }

    private companion object {
        // Material's scale uses 400, 500 and 700; the theme adds its own regular and strong weights.
        val BODY_WEIGHTS = setOf(400, 500, 700)

        /** Family name in themes.json -> (upright file, italic file or null), as packaged in assets. */
        val FILES = mapOf(
            "Archivo" to ("fonts/archivo/Archivo[wdth,wght].ttf" to "fonts/archivo/Archivo-Italic[wdth,wght].ttf"),
            "Plus Jakarta Sans" to ("fonts/plusjakartasans/PlusJakartaSans[wght].ttf" to null),
            "Space Grotesk" to ("fonts/spacegrotesk/SpaceGrotesk[wght].ttf" to null),
            "Outfit" to ("fonts/outfit/Outfit[wght].ttf" to null),
        )
    }
}
