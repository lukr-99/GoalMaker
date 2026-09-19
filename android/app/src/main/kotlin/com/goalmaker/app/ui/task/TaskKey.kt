package com.goalmaker.app.ui.task

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One task's detail view (docs/archive.md). */
@Serializable
data class TaskKey(val id: String) : NavKey
