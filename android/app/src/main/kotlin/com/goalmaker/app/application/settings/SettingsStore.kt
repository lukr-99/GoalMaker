package com.goalmaker.app.application.settings

import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.domain.settings.Appearance
import kotlinx.coroutines.flow.StateFlow

/** Device-local settings that are not synced: the appearance and, in dev builds, a backend override. */
interface SettingsStore {
    val appearance: StateFlow<Appearance>

    fun updateAppearance(change: (Appearance) -> Appearance)

    /** Dev builds only: another Supabase project to use from the next app start. */
    fun backendOverride(): BackendEnvironment?

    fun setBackendOverride(environment: BackendEnvironment?)
}
