package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.UnlockAvailability
import com.goalmaker.app.domain.design.ThemeDefinition
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.Appearance
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
    /** The theme in use: the chosen one, or the default when none is chosen or it's unknown. */
    val themeId: String,
    val themes: List<ThemeDefinition>,
    val email: String,
    /** Whether the app asks to be unlocked when it comes back to the screen. */
    val appLock: Boolean,
    /** What this phone can ask for, which decides whether the lock can be turned on at all. */
    val unlock: UnlockAvailability,
    val signingOut: Boolean,
    val unsyncedAtSignOut: Int?,
    val update: UpdateUiState,
    val appInfo: AppInfo,
    val backendUrlDraft: String,
    val backendKeyDraft: String,
    /** What the Your data card is doing: nothing, asking about a file, or saying what happened. */
    val backup: BackupUiState = BackupUiState(),
)
