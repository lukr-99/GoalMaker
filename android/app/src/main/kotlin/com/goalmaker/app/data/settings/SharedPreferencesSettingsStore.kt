package com.goalmaker.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.ReviewReminder
import com.goalmaker.app.domain.planning.RitualReminder
import com.goalmaker.app.domain.settings.Appearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalTime

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

    private val startHour = MutableStateFlow(
        preferences.getInt(DAY_START_HOUR, PlanningDay.DEFAULT_START_HOUR).coerceIn(PlanningDay.START_HOURS),
    )
    override val dayStartHour: StateFlow<Int> = startHour.asStateFlow()

    override fun setDayStartHour(hour: Int) {
        val valid = hour.coerceIn(PlanningDay.START_HOURS)
        preferences.edit { putInt(DAY_START_HOUR, valid) }
        startHour.value = valid
    }

    private val quiet = MutableStateFlow(readQuietHours())
    override val quietHours: StateFlow<QuietHours> = quiet.asStateFlow()

    override fun setQuietHours(window: QuietHours) {
        preferences.edit {
            putInt(QUIET_START, window.start.toSecondOfDay())
            putInt(QUIET_END, window.end.toSecondOfDay())
        }
        quiet.value = window
    }

    private val planTomorrow = MutableStateFlow(readPlanTomorrow())
    private val weeklyReview = MutableStateFlow(readReviewTime(WEEKLY_REVIEW_AT))
    private val weeklyReviewDay = MutableStateFlow(preferences.getInt(WEEKLY_REVIEW_DAY, ReviewReminder.DEFAULT_WEEKDAY))
    private val monthlyReview = MutableStateFlow(readReviewTime(MONTHLY_REVIEW_AT))
    override val planTomorrowReminder: StateFlow<LocalTime?> = planTomorrow.asStateFlow()

    override val weeklyReviewReminder: StateFlow<LocalTime?> = weeklyReview.asStateFlow()

    override val weeklyReviewWeekday: StateFlow<Int> = weeklyReviewDay.asStateFlow()

    override val monthlyReviewReminder: StateFlow<LocalTime?> = monthlyReview.asStateFlow()

    override fun setWeeklyReviewReminder(time: LocalTime?) {
        preferences.edit { putInt(WEEKLY_REVIEW_AT, time?.toSecondOfDay() ?: OFF) }
        weeklyReview.value = time
    }

    override fun setWeeklyReviewWeekday(weekday: Int) {
        val kept = weekday.coerceIn(1, 7)
        preferences.edit { putInt(WEEKLY_REVIEW_DAY, kept) }
        weeklyReviewDay.value = kept
    }

    override fun setMonthlyReviewReminder(time: LocalTime?) {
        preferences.edit { putInt(MONTHLY_REVIEW_AT, time?.toSecondOfDay() ?: OFF) }
        monthlyReview.value = time
    }

    override fun setPlanTomorrowReminder(time: LocalTime?) {
        preferences.edit { putInt(PLAN_TOMORROW_AT, time?.toSecondOfDay() ?: OFF) }
        planTomorrow.value = time
    }

    private val lock = MutableStateFlow(preferences.getBoolean(APP_LOCK, false))
    override val appLock: StateFlow<Boolean> = lock.asStateFlow()

    override fun setAppLock(on: Boolean) {
        preferences.edit { putBoolean(APP_LOCK, on) }
        lock.value = on
    }

    override fun remindedUntil(): Instant? =
        preferences.getLong(REMINDED_UNTIL, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    override fun setRemindedUntil(instant: Instant) {
        preferences.edit { putLong(REMINDED_UNTIL, instant.toEpochMilli()) }
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

    private fun readQuietHours(): QuietHours {
        val start = preferences.getInt(QUIET_START, 0)
        val end = preferences.getInt(QUIET_END, 0)
        return QuietHours(LocalTime.ofSecondOfDay(start.toLong()), LocalTime.ofSecondOfDay(end.toLong()))
    }

    private fun readReviewTime(key: String): LocalTime? {
        if (!preferences.contains(key)) return ReviewReminder.DEFAULT_TIME
        val seconds = preferences.getInt(key, OFF)
        return if (seconds in 0 until SECONDS_PER_DAY) LocalTime.ofSecondOfDay(seconds.toLong()) else null
    }

    private fun readPlanTomorrow(): LocalTime? {
        if (!preferences.contains(PLAN_TOMORROW_AT)) return RitualReminder.DEFAULT_TIME
        val seconds = preferences.getInt(PLAN_TOMORROW_AT, OFF)
        return if (seconds in 0 until SECONDS_PER_DAY) LocalTime.ofSecondOfDay(seconds.toLong()) else null
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
        const val DAY_START_HOUR = "day_start_hour"
        const val QUIET_START = "quiet_hours_start"
        const val QUIET_END = "quiet_hours_end"
        const val REMINDED_UNTIL = "reminded_until"
        const val APP_LOCK = "app_lock"
        const val PLAN_TOMORROW_AT = "plan_tomorrow_reminder"
        const val WEEKLY_REVIEW_AT = "weekly_review_reminder"
        const val WEEKLY_REVIEW_DAY = "weekly_review_weekday"
        const val MONTHLY_REVIEW_AT = "monthly_review_reminder"
        const val OFF = -1
        const val SECONDS_PER_DAY = 86_400
        const val BACKEND_URL = "dev_backend_url"
        const val BACKEND_KEY = "dev_backend_key"

        inline fun <reified T : Enum<T>> enumOrDefault(stored: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == stored } ?: default
    }
}
