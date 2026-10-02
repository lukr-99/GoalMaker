package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings jump list: its sections, when it shows, where the update deep link lands, and the section in view. */
class SettingsSectionsTest {
    private val everyday = listOf(
        SettingsSection.ACCOUNT,
        SettingsSection.APPEARANCE,
        SettingsSection.PLANNING,
        SettingsSection.AREAS,
        SettingsSection.CLAUDE,
        SettingsSection.DATA,
        SettingsSection.UPDATES,
        SettingsSection.ABOUT,
    )

    // Cards 500 px apart, and the 80 dp line at 200 px (2.5x).
    private val tops = everyday.mapIndexed { index, section -> section to index * 500 }.toMap()
    private val line = 200

    @Test
    fun `a release build lists the everyday sections in screen order`() {
        assertEquals(everyday, SettingsSections.visible(hasProblems = false, devBuild = false))
    }

    @Test
    fun `areas and tags have a section of their own`() {
        val sections = SettingsSections.visible(hasProblems = false, devBuild = false)
        assertEquals(SettingsSection.PLANNING, sections[sections.indexOf(SettingsSection.AREAS) - 1])
    }

    @Test
    fun `problems come first while there are some, and developer last in a dev build`() {
        val sections = SettingsSections.visible(hasProblems = true, devBuild = true)
        assertEquals(listOf(SettingsSection.PROBLEMS) + everyday + SettingsSection.DEVELOPER, sections)
    }

    @Test
    fun `the chips show with four sections or more`() {
        assertTrue(SettingsSections.showChips(everyday))
        assertTrue(SettingsSections.showChips(everyday.take(4)))
        assertFalse(SettingsSections.showChips(everyday.take(3)))
    }

    @Test
    fun `the update notification lands on Updates, for show and for install`() {
        assertEquals(SettingsSection.UPDATES, SettingsSections.target(UpdateRequest.SHOW))
        assertEquals(SettingsSection.UPDATES, SettingsSections.target(UpdateRequest.INSTALL))
        assertNull(SettingsSections.target(null))
    }

    @Test
    fun `Updates is always in the jump list, so the deep link has somewhere to land`() {
        for (problems in listOf(false, true)) {
            for (dev in listOf(false, true)) {
                assertTrue(SettingsSection.UPDATES in SettingsSections.visible(problems, dev))
            }
        }
    }

    @Test
    fun `the section in view is the last one whose top passed the line below the top`() {
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, tops, scroll = 0, maxScroll = 5_000, line = line))
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, tops, scroll = 299, maxScroll = 5_000, line = line))
        assertEquals(SettingsSection.APPEARANCE, SettingsSections.current(everyday, tops, scroll = 300, maxScroll = 5_000, line = line))
        assertEquals(SettingsSection.AREAS, SettingsSections.current(everyday, tops, scroll = 1_300, maxScroll = 5_000, line = line))
    }

    @Test
    fun `at the bottom the last section is current even if its top never reached the line`() {
        assertEquals(SettingsSection.ABOUT, SettingsSections.current(everyday, tops, scroll = 3_000, maxScroll = 3_000, line = line))
    }

    @Test
    fun `the jump target stays current, at the bottom too`() {
        val updates = SettingsSections.current(everyday, tops, scroll = 3_000, maxScroll = 3_000, line = line, pinned = SettingsSection.UPDATES)
        assertEquals(SettingsSection.UPDATES, updates)
        val midway = SettingsSections.current(everyday, tops, scroll = 600, maxScroll = 3_000, line = line, pinned = SettingsSection.CLAUDE)
        assertEquals(SettingsSection.CLAUDE, midway)
    }

    @Test
    fun `before anything is measured the first section is current`() {
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, emptyMap(), scroll = 0, maxScroll = 0, line = line))
        assertNull(SettingsSections.current(emptyList(), emptyMap(), scroll = 0, maxScroll = 0, line = line))
    }
}
