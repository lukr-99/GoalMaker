package com.goalmaker.app.ui.projects

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.ui.theme.AppTheme

/**
 * How a project item's type looks wherever it shows (docs/projects.md): on the board, and on the
 * project chip a task row wears (docs/lists.md). An idea and a bug carry their own icon and colour.
 */
object ItemTypeLook {
    /** The icon an item's type wears: a bug, a lightbulb, or a plain task. */
    fun icon(itemType: String): ImageVector = when (itemType) {
        ProjectRules.IDEA -> Icons.Outlined.Lightbulb
        ProjectRules.BUG -> Icons.Outlined.BugReport
        else -> Icons.Outlined.TaskAlt
    }

    /** A bug reads as a problem, an idea as something to pick up, and a task keeps the quiet colour. */
    @Composable
    fun color(itemType: String): Color = when (itemType) {
        ProjectRules.IDEA -> AppTheme.colors.accent
        ProjectRules.BUG -> AppTheme.colors.danger
        else -> AppTheme.colors.textMuted
    }
}
