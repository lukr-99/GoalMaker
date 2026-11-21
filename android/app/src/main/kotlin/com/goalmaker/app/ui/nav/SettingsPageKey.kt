package com.goalmaker.app.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One of the pages Settings is split into, by its id (ui/settings/SettingsPage.kt). */
@Serializable
data class SettingsPageKey(val page: String) : NavKey
