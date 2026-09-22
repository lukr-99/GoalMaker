package com.goalmaker.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R

/** The bottom bar every main screen carries, so all five places are one tap away. */
@Composable
fun MainNavigationBar(current: MainDestination, onSelect: (MainDestination) -> Unit) {
    NavigationBar {
        MainDestination.entries.forEach { entry ->
            NavigationBarItem(
                selected = entry == current,
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon(), contentDescription = null) },
                label = { Text(stringResource(entry.title())) },
            )
        }
    }
}

private fun MainDestination.title() = when (this) {
    MainDestination.TODAY -> R.string.lists_today
    MainDestination.TOMORROW -> R.string.lists_tomorrow
    MainDestination.INBOX -> R.string.lists_inbox
    MainDestination.PROJECTS -> R.string.projects_title
    MainDestination.CALENDAR -> R.string.calendar_title
}

private fun MainDestination.icon() = when (this) {
    MainDestination.TODAY -> Icons.Outlined.Today
    MainDestination.TOMORROW -> Icons.Outlined.WbTwilight
    MainDestination.INBOX -> Icons.Outlined.Inbox
    MainDestination.PROJECTS -> Icons.Outlined.Dashboard
    MainDestination.CALENDAR -> Icons.Outlined.CalendarMonth
}
