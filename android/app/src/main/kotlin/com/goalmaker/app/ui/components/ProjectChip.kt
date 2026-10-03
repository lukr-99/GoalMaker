package com.goalmaker.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.ui.projects.ItemTypeLook
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The chip a project item wears in a task list, the calendar's day and the archive (docs/lists.md):
 * the project's name in a quiet outline, with an idea's or a bug's icon as on the board and the
 * board's own icon for a plain task. With [onOpen] it opens the project's board; a screen reader
 * hears "Project GoalMaker" (or "Idea for", "Bug in") and the open action.
 */
@Composable
fun ProjectChip(project: ProjectItem, itemType: String, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val label = projectLabel(project, itemType)
    val openLabel = stringResource(R.string.lists_open_project, project.name)
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(shape)
            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.6f), shape)
            .then(if (onOpen == null) Modifier else Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen))
            .clearAndSetSemantics {
                contentDescription = label
                if (onOpen != null) {
                    role = Role.Button
                    onClick(label = openLabel) { onOpen(); true }
                }
            }
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Icon(
            if (itemType == ProjectRules.TASK) Icons.Outlined.Dashboard else ItemTypeLook.icon(itemType),
            contentDescription = null,
            tint = ItemTypeLook.color(itemType),
            modifier = Modifier.size(12.dp),
        )
        Text(
            project.name,
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

/** "Project GoalMaker", "Idea for GoalMaker" or "Bug in GoalMaker", what a screen reader hears for the chip. */
@Composable
fun projectLabel(project: ProjectItem, itemType: String): String = stringResource(
    when (itemType) {
        ProjectRules.IDEA -> R.string.lists_project_idea
        ProjectRules.BUG -> R.string.lists_project_bug
        else -> R.string.lists_project_task
    },
    project.name,
)
