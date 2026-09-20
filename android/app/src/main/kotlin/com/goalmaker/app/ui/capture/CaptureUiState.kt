package com.goalmaker.app.ui.capture

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectItem

/**
 * What the share sheet needs: whether there is anyone to save for, and the areas, tags and projects
 * the line can name. [loaded] stays false until the session is known, so the sheet doesn't flash.
 */
data class CaptureUiState(
    val loaded: Boolean = false,
    val signedIn: Boolean = false,
    val areas: List<AreaItem> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
)
