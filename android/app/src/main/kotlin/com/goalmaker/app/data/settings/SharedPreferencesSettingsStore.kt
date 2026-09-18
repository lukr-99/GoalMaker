package com.goalmaker.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.settings.Appearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** [SettingsStore] in private SharedPreferences. None of this is synced or backed up. */
class SharedPreferencesSettingsStore(private val preferences: SharedPreferences) : SettingsStore {

    private val current = MutableStateFlow(readAppearance())
    override val appearance: StateFlow<Appearance> = current.asStateFlow()

    override fun updateAppearance(change: (Appearance) -> Appearance) {
        current.update { before ->
            change(before).also { after ->
                preferences.edit {
                    putString(THEME, after.themeId)
                    putString(THEME_MODE, after.mode.name)
                    putBoolean(PURE_BLACK, after.pureBlack)
                    putString(REDUCE_MOTION, after.reduceMotion.name)
                    putBoolean(COMPLETION_SOUND, after.completionSound)
                }
            }
        }
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

    private fun readAppearance() = Appearance(
        themeId = preferences.getString(THEME, null),
        mode = enumOrDefault(preferences.getString(THEME_MODE, null), Appearance.DEFAULT.mode),
        pureBlack = preferences.getBoolean(PURE_BLACK, Appearance.DEFAULT.pureBlack),
        reduceMotion = enumOrDefault(preferences.getString(REDUCE_MOTION, null), Appearance.DEFAULT.reduceMotion),
        completionSound = preferences.getBoolean(COMPLETION_SOUND, Appearance.DEFAULT.completionSound),
    )

    private companion object {
        const val THEME = "theme"
        const val THEME_MODE = "theme_mode"
        const val PURE_BLACK = "pure_black"
        const val REDUCE_MOTION = "reduce_motion"
        const val COMPLETION_SOUND = "completion_sound"
        const val BACKEND_URL = "dev_backend_url"
        const val BACKEND_KEY = "dev_backend_key"

        inline fun <reified T : Enum<T>> enumOrDefault(stored: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == stored } ?: default
    }
}
