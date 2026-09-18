package com.goalmaker.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [SettingsStore] in private SharedPreferences. None of this is synced or backed up. */
class SharedPreferencesSettingsStore(private val preferences: SharedPreferences) : SettingsStore {

    private val theme = MutableStateFlow(readThemeMode())
    override val themeMode: StateFlow<ThemeMode> = theme.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(THEME_MODE, mode.name) }
        theme.value = mode
    }

    override fun backendOverride(): BackendEnvironment? {
        val url = preferences.getString(BACKEND_URL, null) ?: return null
        val key = preferences.getString(BACKEND_KEY, null) ?: return null
        return BackendEnvironment(url, key).takeIf { it.isConfigured }
    }

    override fun setBackendOverride(environment: BackendEnvironment?) {
        preferences.edit(commit = true) {
            if (environment == null) {
                remove(BACKEND_URL)
                remove(BACKEND_KEY)
            } else {
                putString(BACKEND_URL, environment.url.trim())
                putString(BACKEND_KEY, environment.publishableKey.trim())
            }
        }
    }

    private fun readThemeMode(): ThemeMode =
        preferences.getString(THEME_MODE, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val THEME_MODE = "theme_mode"
        const val BACKEND_URL = "dev_backend_url"
        const val BACKEND_KEY = "dev_backend_key"
    }
}
