package com.goalmaker.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.goalmaker.app.R
import com.goalmaker.app.domain.navigation.PlaceRules
import com.goalmaker.app.ui.lists.ListTab

/** How each place looks in the bottom bar and on the Places hub, and which list a place is. */
object PlaceLook {
    /** The Places hub's own id; it is the bar's fifth tab, not a place that can be pinned. */
    const val HUB = "places"

    /** The screen the three lists share, as [MainScreen] hands it to its content. */
    const val LISTS = "lists"

    @StringRes
    fun title(place: String): Int = when (place) {
        PlaceRules.TODAY -> R.string.lists_today
        PlaceRules.TOMORROW -> R.string.lists_tomorrow
        PlaceRules.INBOX -> R.string.lists_inbox
        PlaceRules.CALENDAR -> R.string.calendar_title
        PlaceRules.HABITS -> R.string.habits_title
        PlaceRules.GOALS -> R.string.goals_title
        PlaceRules.LIFE_GOALS -> R.string.life_goals_title
        PlaceRules.PROJECTS -> R.string.projects_title
        PlaceRules.WANTS -> R.string.wants_title
        PlaceRules.TALLY -> R.string.tally_title
        PlaceRules.REVIEWS -> R.string.reviews_title
        PlaceRules.STATS -> R.string.stats_title
        PlaceRules.ARCHIVE -> R.string.archive_title
        else -> R.string.places_title
    }

    fun icon(place: String): ImageVector = when (place) {
        PlaceRules.TODAY -> Icons.Outlined.Today
        PlaceRules.TOMORROW -> Icons.Outlined.WbTwilight
        PlaceRules.INBOX -> Icons.Outlined.Inbox
        PlaceRules.CALENDAR -> Icons.Outlined.CalendarMonth
        PlaceRules.HABITS -> Icons.Outlined.DonutLarge
        PlaceRules.GOALS -> Icons.Outlined.Flag
        PlaceRules.LIFE_GOALS -> Icons.Outlined.AutoAwesome
        PlaceRules.PROJECTS -> Icons.Outlined.Dashboard
        PlaceRules.WANTS -> Icons.Outlined.ShoppingBag
        PlaceRules.TALLY -> Icons.Outlined.Timelapse
        PlaceRules.REVIEWS -> Icons.AutoMirrored.Outlined.MenuBook
        PlaceRules.STATS -> Icons.AutoMirrored.Outlined.TrendingUp
        PlaceRules.ARCHIVE -> Icons.Outlined.Archive
        else -> Icons.Outlined.Apps
    }

    /** The list a place is, when it is one of the three lists that share one screen. */
    fun tab(place: String): ListTab? = when (place) {
        PlaceRules.TODAY -> ListTab.TODAY
        PlaceRules.TOMORROW -> ListTab.TOMORROW
        PlaceRules.INBOX -> ListTab.INBOX
        else -> null
    }
}
