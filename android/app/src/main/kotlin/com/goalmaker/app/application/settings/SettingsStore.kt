package com.goalmaker.app.application.settings

import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.Appearance
import kotlinx.coroutines.flow.StateFlow

/**
 * Device-local settings that are not synced: the appearance, when the planning day starts, quiet
 * hours and, in dev builds, a backend override.
 */
interface SettingsStore {
    val appearance: StateFlow<Appearance>

    fun updateAppearance(change: (Appearance) -> Appearance)

    /** The hour the planning day starts (docs/lists.md), 0 to 6; 4 unless changed. */
    val dayStartHour: StateFlow<Int>

    fun setDayStartHour(hour: Int)

    /** The window that holds ordinary reminders back (docs/reminders.md); off unless set. */
    val quietHours: StateFlow<QuietHours>

    fun setQuietHours(window: QuietHours)

    /** Dev builds only: another Supabase project to use from the next app start. */
    fun backendOverride(): BackendEnvironment?

    fun setBackendOverride(environment: BackendEnvironment?)
}
