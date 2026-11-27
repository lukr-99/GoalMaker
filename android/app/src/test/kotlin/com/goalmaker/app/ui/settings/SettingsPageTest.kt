package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages Settings is split into, Tarot2Go style (docs/design/spec.md, Settings). */
class SettingsPageTest {
    @Test
    fun `every section is on a page, and each group with a page of its own is a link on the main one`() {
        val pages = SettingsPage.entries - SettingsPage.MAIN
        assertEquals(SettingsSection.entries.toSet(), SettingsPage.entries.flatMap { it.sections }.toSet())
        val onPages = pages.flatMap { it.sections }
        assertEquals("no section is on two pages", onPages.size, onPages.toSet().size)
        for (page in pages) {
            val link = page.sections.first()
            assertEquals(page, SettingsPage.opens(link))
            assertTrue(link in SettingsPage.MAIN.sections)
        }
    }

    @Test
    fun `the main page links to the crowded groups and keeps the rest`() {
        val main = SettingsSections.visible(hasProblems = false, devBuild = false)
        assertEquals(
            listOf("account", "appearance", "planning", "areas", "claude", "data", "updates", "about"),
            main.map(SettingsSection::id),
        )
        assertEquals(SettingsPage.APPEARANCE, SettingsPage.opens(SettingsSection.APPEARANCE))
        assertEquals(SettingsPage.PLANNING, SettingsPage.opens(SettingsSection.PLANNING))
        assertEquals(SettingsPage.DATA, SettingsPage.opens(SettingsSection.DATA))
        assertEquals(SettingsPage.DATA, SettingsPage.opens(SettingsSection.UPDATES))
        assertNull(SettingsPage.opens(SettingsSection.ACCOUNT))
        assertNull(SettingsPage.opens(SettingsSection.AREAS))
        assertTrue(SettingsSections.showChips(main))
    }

    @Test
    fun `the main page has an Updates card of its own that opens the page with the update rows (GM-123)`() {
        val main = SettingsSections.visible(hasProblems = false, devBuild = false)
        assertTrue(SettingsSection.UPDATES in main)
        assertEquals(SettingsPage.DATA, SettingsPage.opens(SettingsSection.UPDATES))
        assertTrue(SettingsSection.UPDATES in SettingsPage.DATA.sections)
    }

    @Test
    fun `each page shows its own cards without the chip row`() {
        assertEquals(listOf(SettingsSection.APPEARANCE), SettingsSections.visible(false, false, SettingsPage.APPEARANCE))
        assertEquals(listOf(SettingsSection.PLANNING), SettingsSections.visible(false, false, SettingsPage.PLANNING))
        val data = SettingsSections.visible(false, false, SettingsPage.DATA)
        assertEquals(listOf(SettingsSection.DATA, SettingsSection.UPDATES), data)
        assertTrue(!SettingsSections.showChips(data))
    }

    @Test
    fun `the update notification opens the page with Updates on it`() {
        val page = SettingsSections.UPDATE_PAGE
        assertTrue(SettingsSections.target(UpdateRequest.INSTALL)!! in page.sections)
    }

    @Test
    fun `an unknown page id is the main page`() {
        assertEquals(SettingsPage.DATA, SettingsPage.of("data"))
        assertEquals(SettingsPage.MAIN, SettingsPage.of("gone"))
    }
}
