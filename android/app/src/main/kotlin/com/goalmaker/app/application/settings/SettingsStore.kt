package com.goalmaker.app.application.settings

import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.Appearance
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow

/**
 * Device-local settings that are not synced: the appearance, when the planning day starts, quiet
 * hours, the evening reminder and, in dev builds, a backend override.
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

    /** When the evening Plan tomorrow reminder rings (docs/reminders.md); 20:00 unless changed, null when off. */
    val planTomorrowReminder: StateFlow<LocalTime?>

    fun setPlanTomorrowReminder(time: LocalTime?)

    /** When this device last looked at its reminders, so each one is shown once (docs/reminders.md). */
    fun remindedUntil(): Instant?

    fun setRemindedUntil(instant: Instant)

    /** Dev builds only: another Supabase project to use from the next app start. */
    fun backendOverride(): BackendEnvironment?

    fun setBackendOverride(environment: BackendEnvironment?)
}
