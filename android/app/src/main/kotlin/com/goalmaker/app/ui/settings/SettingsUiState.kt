package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.domain.settings.ThemeMode

/**
 * Everything the settings screen shows. [unsyncedAtSignOut] is set when sign-out stopped because
 * changes haven't reached the server; the screen then offers to sign out anyway.
 */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val email: String,
    val signingOut: Boolean,
    val unsyncedAtSignOut: Int?,
    val update: UpdateUiState,
    val appInfo: AppInfo,
    val backendUrlDraft: String,
    val backendKeyDraft: String,
)
