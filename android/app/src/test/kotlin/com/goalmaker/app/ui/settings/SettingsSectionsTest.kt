package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Settings jump list: its sections, where the update deep link lands, and the section in view. */
class SettingsSectionsTest {
    private val everyday = listOf(
        SettingsSection.ACCOUNT,
        SettingsSection.APPEARANCE,
        SettingsSection.PLANNING,
        SettingsSection.AREAS,
        SettingsSection.CLAUDE,
        SettingsSection.BACKUP,
        SettingsSection.UPDATES,
        SettingsSection.ABOUT,
    )

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
    fun `the update notification lands on Updates, for show and for install`() {
        assertEquals(SettingsSection.UPDATES, SettingsSections.target(UpdateRequest.SHOW))
        assertEquals(SettingsSection.UPDATES, SettingsSections.target(UpdateRequest.INSTALL))
        assertNull(SettingsSections.target(null))
    }

    @Test
    fun `Updates is always in the jump list, so the deep link has somewhere to land`() {
        for (problems in listOf(false, true)) {
            for (dev in listOf(false, true)) {
                val sections = SettingsSections.visible(problems, dev)
                assertEquals(true, SettingsSection.UPDATES in sections)
            }
        }
    }

    @Test
    fun `the section in view is the last one whose top has scrolled up`() {
        val tops = everyday.mapIndexed { index, section -> section to index * 500 }.toMap()
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, tops, scroll = 0, maxScroll = 5_000))
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, tops, scroll = 499, maxScroll = 5_000))
        assertEquals(SettingsSection.APPEARANCE, SettingsSections.current(everyday, tops, scroll = 500, maxScroll = 5_000))
        assertEquals(SettingsSection.AREAS, SettingsSections.current(everyday, tops, scroll = 1_480, maxScroll = 5_000, slack = 24))
    }

    @Test
    fun `at the bottom the last section is current even if its top never reached the top`() {
        val tops = everyday.mapIndexed { index, section -> section to index * 500 }.toMap()
        assertEquals(SettingsSection.ABOUT, SettingsSections.current(everyday, tops, scroll = 3_000, maxScroll = 3_000))
    }

    @Test
    fun `a jump to a section near the bottom keeps that section marked`() {
        val tops = everyday.mapIndexed { index, section -> section to index * 500 }.toMap()
        val updates = SettingsSections.current(everyday, tops, scroll = 3_000, maxScroll = 3_000, jumped = SettingsSection.UPDATES)
        assertEquals(SettingsSection.UPDATES, updates)
        // Once the page has moved past it, the jump no longer counts.
        val past = SettingsSections.current(everyday, tops, scroll = 3_000, maxScroll = 3_000, jumped = SettingsSection.APPEARANCE)
        assertEquals(SettingsSection.ABOUT, past)
    }

    @Test
    fun `before anything is measured the first section is current`() {
        assertEquals(SettingsSection.ACCOUNT, SettingsSections.current(everyday, emptyMap(), scroll = 0, maxScroll = 0))
        assertNull(SettingsSections.current(emptyList(), emptyMap(), scroll = 0, maxScroll = 0))
    }
}
