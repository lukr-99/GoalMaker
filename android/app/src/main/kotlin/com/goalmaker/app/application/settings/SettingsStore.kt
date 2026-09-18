package com.goalmaker.app.application.settings

import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.domain.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** Device-local settings that are not synced: the theme and, in dev builds, a backend override. */
interface SettingsStore {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)

    /** Dev builds only: another Supabase project to use from the next app start. */
    fun backendOverride(): BackendEnvironment?

    fun setBackendOverride(environment: BackendEnvironment?)
}
