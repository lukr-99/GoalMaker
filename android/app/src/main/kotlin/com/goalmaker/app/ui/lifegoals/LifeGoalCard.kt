package com.goalmaker.app.ui.lifegoals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.ui.theme.AppTheme

/**
 * One life goal (docs/life-goals.md, "On screen"): its pictures to swipe through, or the accent with
 * its first letter when it has none; the title, the why in full and the time left. The menu edits,
 * marks it achieved, drops, moves, reopens or deletes it.
 */
@Composable
fun LifeGoalCard(
    row: LifeGoalRow,
    pictureVersion: Int,
    loadPicture: suspend (String) -> ByteArray?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onAchieve: () -> Unit,
    onDrop: () -> Unit,
    onReopen: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = row.goal
    val colors = AppTheme.colors
    val open = goal.status == LifeGoalRules.OPEN
    Surface(shape = AppTheme.shapes.card, color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onEdit)) {
            if (open) Pictures(row, pictureVersion, loadPicture)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = AppTheme.density.cardPadding.dp, top = 12.dp)) {
                Column(Modifier.weight(1f).padding(bottom = AppTheme.density.cardPadding.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.text)
                    val status = when (goal.status) {
                        LifeGoalRules.ACHIEVED -> stringResource(R.string.life_goals_status_achieved)
                        LifeGoalRules.DROPPED -> stringResource(R.string.life_goals_status_dropped)
                        else -> row.timeLeft?.let { timeLeftText(it) }
                    }
                    val maker = if (goal.madeBy == ProjectRules.CLAUDE) stringResource(R.string.life_goals_by_claude) else null
                    listOfNotNull(status, maker).takeIf { it.isNotEmpty() }?.let { parts ->
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (open) colors.accent else colors.textMuted,
                        )
                    }
                    if (open) {
                        Text(
                            goal.why,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Menu(row, canMoveUp, canMoveDown, onEdit, onAchieve, onDrop, onReopen, onMove, onDelete)
            }
        }
    }
}

@Composable
private fun Pictures(row: LifeGoalRow, version: Int, load: suspend (String) -> ByteArray?) {
    val pictures = row.pictures
    val box = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(AppTheme.shapes.card)
    if (pictures.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = box.background(AppTheme.colors.accent.copy(alpha = 0.18f))) {
            Text(
                row.goal.title.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.accent,
            )
        }
        return
    }
    val pager = rememberPagerState { pictures.size }
    Box(box) {
        HorizontalPager(state = pager, key = { pictures[it].id }, modifier = Modifier.fillMaxSize()) { page ->
            PictureImage(
                id = pictures[page].id,
                version = version,
                description = stringResource(R.string.life_goals_picture, page + 1, pictures.size, row.goal.title),
                load = load,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (pictures.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                repeat(pictures.size) { index ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (index == pager.currentPage) Color.White else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Menu(
    row: LifeGoalRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onAchieve: () -> Unit,
    onDrop: () -> Unit,
    onReopen: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.life_goals_menu, row.goal.title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Choice(label: String, danger: Boolean = false, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label, color = if (danger) AppTheme.colors.danger else MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    open = false
                    action()
                },
            )
            Choice(stringResource(R.string.life_goals_edit), action = onEdit)
            if (row.goal.status == LifeGoalRules.OPEN) {
                Choice(stringResource(R.string.life_goals_achieve), action = onAchieve)
                Choice(stringResource(R.string.life_goals_drop), action = onDrop)
                if (canMoveUp) Choice(stringResource(R.string.life_goals_move_up)) { onMove(-1) }
                if (canMoveDown) Choice(stringResource(R.string.life_goals_move_down)) { onMove(1) }
            } else {
                Choice(stringResource(R.string.life_goals_reopen), action = onReopen)
            }
            Choice(stringResource(R.string.life_goals_delete), danger = true, action = onDelete)
        }
    }
}
