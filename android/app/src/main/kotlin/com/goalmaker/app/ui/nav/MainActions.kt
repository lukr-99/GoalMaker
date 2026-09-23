package com.goalmaker.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.goalmaker.app.R
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.ui.lists.SyncIndicator

/**
 * The top bar's actions, the same on every place the bottom bar reaches: the sync cloud, Habits and
 * Goals under their own marks, the menu with the reviews, the stats and the archive, Plan tomorrow,
 * and Settings with its mark for a problem nobody has read yet (docs/problems.md).
 */
@Composable
fun MainActions(
    sync: SyncStatus,
    onSyncNow: () -> Unit,
    hasProblems: Boolean,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val waiting = stringResource(R.string.problems_waiting)
    SyncIndicator(sync, onSyncNow = onSyncNow)
    IconButton(onClick = onOpenHabits) {
        Icon(Icons.Outlined.DonutLarge, contentDescription = stringResource(R.string.habits_title))
    }
    IconButton(onClick = onOpenGoals) {
        Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.goals_title))
    }
    MoreMenu(onOpenArchive = onOpenArchive, onOpenReviews = onOpenReviews, onOpenStats = onOpenStats)
    IconButton(onClick = onOpenPlan) {
        Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.plan_title))
    }
    IconButton(onClick = onOpenSettings) {
        BadgedBox(
            badge = { if (hasProblems) Badge(modifier = Modifier.semantics { contentDescription = waiting }) },
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.today_settings))
        }
    }
}

/** What doesn't fit the top bar: the reviews, the stats and the archive. */
@Composable
private fun MoreMenu(
    onOpenArchive: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.lists_more_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reviews_title)) },
                leadingIcon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                onClick = {
                    open = false
                    onOpenReviews()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_title)) },
                leadingIcon = { Icon(Icons.Outlined.TrendingUp, contentDescription = null) },
                onClick = {
                    open = false
                    onOpenStats()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_title)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = {
                    open = false
                    onOpenArchive()
                },
            )
        }
    }
}
