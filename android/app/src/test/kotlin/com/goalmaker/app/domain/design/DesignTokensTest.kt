package com.goalmaker.app.domain.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    private val tokens = DesignTokens.parse(
        File(System.getProperty("goalmaker.contracts")!!, "design/themes.json").readText(),
    )

    @Test
    fun `the four themes load with Track as the default`() {
        assertEquals(listOf("track", "electric", "night", "sunrise"), tokens.themes.map { it.id })
        assertEquals("track", tokens.theme(null).id)
        assertEquals("track", tokens.theme("retired-theme").id)
        assertEquals("night", tokens.theme("night").id)
    }

    @Test
    fun `colors become opaque ARGB`() {
        val track = tokens.theme("track")
        assertEquals(0xFFD6FF3A.toInt(), track.dark.primary)
        assertEquals(0xFFFFFFFF.toInt(), track.light.background)
    }

    @Test
    fun `every theme colors the mark, and the mark's shape loads`() {
        assertEquals(LogoColors(0xFF0A0A0A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6FF3A.toInt()), tokens.theme("track").logo)
        assertEquals(0xFFC2185B.toInt(), tokens.theme("sunrise").logo.tile)
        val mark = LogoMark.parse(File(System.getProperty("goalmaker.contracts")!!, "design/logo.json").readText())
        assertEquals(100f, mark.size)
        assertTrue(mark.letter.startsWith("M") && mark.trend.startsWith("M") && mark.head.endsWith("Z"))
    }

    @Test
    fun `pure black keeps the dark palette on black surfaces`() {
        tokens.themes.forEach { theme ->
            assertEquals(0xFF000000.toInt(), theme.black.background)
            assertEquals(theme.dark.text, theme.black.text)
            assertEquals(theme.dark.primary, theme.black.primary)
        }
    }

    @Test
    fun `Track's headings are wide, italic and uppercase`() {
        val heading = tokens.theme("track").typography.heading
        assertEquals("Archivo", heading.family)
        assertEquals(900, heading.weight)
        assertEquals(112.5f, heading.width, 0f)
        assertTrue(heading.italic && heading.uppercase)
    }

    @Test
    fun `twenty area colors with chip pairs for both modes`() {
        assertEquals(20, tokens.areaColors.size)
        assertEquals(0xFF5B21B6.toInt(), tokens.areaColor("violet")!!.light.content)
    }

    @Test
    fun `every theme names a font this repository bundles`() {
        val fonts = File(System.getProperty("goalmaker.contracts")!!).parentFile!!.resolve("fonts")
        val bundled = mapOf(
            "Archivo" to "archivo",
            "Plus Jakarta Sans" to "plusjakartasans",
            "Space Grotesk" to "spacegrotesk",
            "Outfit" to "outfit",
        )
        tokens.themes.flatMap { listOf(it.typography.heading.family, it.typography.body.family, it.typography.number.family) }
            .forEach { family ->
                val folder = bundled[family] ?: error("$family isn't a bundled font")
                assertTrue(family, fonts.resolve(folder).listFiles { f -> f.extension == "ttf" }.orEmpty().isNotEmpty())
            }
    }
}
