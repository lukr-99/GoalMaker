package com.goalmaker.app.ui.activity

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Recent changes, by the owner and by Claude, with undo (docs/activity.md). */
@Serializable
data object ActivityKey : NavKey
