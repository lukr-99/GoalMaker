package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.planning.WhyFrequency
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.UnlockAvailability
import com.goalmaker.app.application.planning.DndBreakthrough
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.domain.design.ThemeDefinition
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.Appearance
import java.time.Instant
import java.time.LocalTime

/**
 * Everything the settings screen shows. [unsyncedAtSignOut] is set when sign-out stopped because
 * changes haven't reached the server; the screen then offers to sign out anyway.
 */
data class SettingsUiState(
    val appearance: Appearance,
    val dayStartHour: Int,
    val quietHours: QuietHours,
    /** When the evening Plan tomorrow reminder rings, or null when it's off. */
    val planTomorrowReminder: LocalTime?,
    val weeklyReviewReminder: LocalTime?,
    val weeklyReviewWeekday: Int,
    val monthlyReviewReminder: LocalTime?,
    /** When the notification for wants that became ready rings, or null when it's off. */
    val wantsReadyReminder: LocalTime? = null,
    val whyReminder: WhyFrequency = WhyFrequency.DEFAULT,
    /** The theme in use: the chosen one, or the default when none is chosen or it's unknown. */
    val themeId: String,
    val themes: List<ThemeDefinition>,
    val email: String,
    /** Whether the app asks to be unlocked when it comes back to the screen. */
    val appLock: Boolean,
    /** What this phone can ask for, which decides whether the lock can be turned on at all. */
    val unlock: UnlockAvailability,
    /** Whether an important reminder rings through Do Not Disturb on this phone (docs/reminders.md). */
    val importantDnd: DndBreakthrough = DndBreakthrough.NOT_ALLOWED,
    val signingOut: Boolean,
    val unsyncedAtSignOut: Int?,
    val update: UpdateUiState,
    /** The update the last check found and nothing has installed yet: the accent row in Updates. */
    val waitingUpdate: UpdateCheckResult.Available? = null,
    /** When a check last reached the update channel, the owner's or the quiet daily one; null before the first. */
    val updatesCheckedAt: Instant? = null,
    /** Until when Later keeps the waiting update quiet, or null when it doesn't. */
    val updatePostponedUntil: Instant? = null,
    /** Whether the waiting update's APK was fetched ahead and still matches, so Install is quick. */
    val updateDownloaded: Boolean = false,
    val appInfo: AppInfo,
    val backendUrlDraft: String,
    val backendKeyDraft: String,
    /** What the Your data card is doing: nothing, asking about a file, or saying what happened. */
    val backup: BackupUiState = BackupUiState(),
)
