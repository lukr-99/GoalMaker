package com.goalmaker.app.data.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPreferencesSettingsStoreTest {
    private val preferences get() =
        RuntimeEnvironment.getApplication().getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() = preferences.edit(commit = true) { clear() }

    @Test
    fun `a fresh install uses the default appearance`() {
        assertEquals(Appearance.DEFAULT, SharedPreferencesSettingsStore(preferences).appearance.value)
    }

    @Test
    fun `changes apply at once and survive a restart`() {
        val store = SharedPreferencesSettingsStore(preferences)

        store.updateAppearance {
            it.copy(themeId = "night", mode = ThemeMode.DARK, pureBlack = true, reduceMotion = ReduceMotion.ON, completionSound = true)
        }

        val expected = Appearance("night", ThemeMode.DARK, pureBlack = true, reduceMotion = ReduceMotion.ON, completionSound = true)
        assertEquals(expected, store.appearance.value)
        assertEquals(expected, SharedPreferencesSettingsStore(preferences).appearance.value)
    }

    @Test
    fun `the light or dark choice saved before themes existed is kept`() {
        preferences.edit(commit = true) { putString("theme_mode", "LIGHT") }

        val appearance = SharedPreferencesSettingsStore(preferences).appearance.value

        assertEquals(ThemeMode.LIGHT, appearance.mode)
        assertEquals(null, appearance.themeId)
    }

    @Test
    fun `unknown stored values fall back to the defaults`() {
        preferences.edit(commit = true) {
            putString("theme_mode", "SEPIA")
            putString("reduce_motion", "MAYBE")
        }

        val appearance = SharedPreferencesSettingsStore(preferences).appearance.value

        assertEquals(ThemeMode.SYSTEM, appearance.mode)
        assertEquals(ReduceMotion.SYSTEM, appearance.reduceMotion)
    }
}
