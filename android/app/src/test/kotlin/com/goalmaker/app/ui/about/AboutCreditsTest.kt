package com.goalmaker.app.ui.about

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The credits card lists every font the app ships, and nothing on it is blank or listed twice. */
class AboutCreditsTest {
    private val credits = AboutCredits.groups.flatMap { it.credits }

    @Test
    fun `every group has credits and every credit a name and a licence`() {
        AboutCredits.groups.forEach { assertTrue(it.credits.isNotEmpty()) }
        credits.forEach { credit ->
            assertTrue(credit.name, credit.name.isNotBlank())
            assertTrue(credit.name, credit.licence.isNotBlank())
        }
    }

    @Test
    fun `no credit is listed twice`() {
        assertEquals(credits.map { it.name }.distinct(), credits.map { it.name })
    }

    @Test
    fun `every font folder in the repository is credited under the font licence`() {
        val fonts = File(System.getProperty("goalmaker.contracts"), "../fonts").listFiles { file -> file.isDirectory }!!
        val credited = credits.filter { it.licence == AboutCredits.OFL }.map { it.name.replace(" ", "").lowercase() }.toSet()
        assertEquals(fonts.map { it.name }.toSet(), credited)
    }
}
