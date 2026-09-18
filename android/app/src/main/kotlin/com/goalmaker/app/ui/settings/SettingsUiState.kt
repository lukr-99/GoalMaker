package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.domain.settings.ThemeMode

/** Everything the settings screen shows. */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val email: String,
    val update: UpdateUiState,
    val appInfo: AppInfo,
    val backendUrlDraft: String,
    val backendKeyDraft: String,
)
