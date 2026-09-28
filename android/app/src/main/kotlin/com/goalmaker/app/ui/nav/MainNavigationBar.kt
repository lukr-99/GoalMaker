package com.goalmaker.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The bottom bar (ADR 0014): the places the owner pinned, then Places, which counts what waits in
 * the places that are not pinned. The selected pill wears the accent.
 */
@Composable
fun MainNavigationBar(pins: List<String>, current: String, placesCount: Int, onSelect: (String) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = AppTheme.colors.accent,
        selectedIconColor = AppTheme.colors.onAccent,
        selectedTextColor = AppTheme.colors.text,
        unselectedIconColor = AppTheme.colors.textMuted,
        unselectedTextColor = AppTheme.colors.textMuted,
    )
    val onHub = current == PlaceLook.HUB || current !in pins
    NavigationBar {
        pins.forEach { place ->
            NavigationBarItem(
                selected = place == current,
                onClick = { onSelect(place) },
                icon = { Icon(PlaceLook.icon(place), contentDescription = null) },
                label = { Text(stringResource(PlaceLook.title(place)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = colors,
            )
        }
        val waiting = if (placesCount > 0) pluralStringResource(R.plurals.places_waiting, placesCount, placesCount) else null
        NavigationBarItem(
            selected = onHub,
            onClick = { onSelect(PlaceLook.HUB) },
            icon = {
                BadgedBox(
                    badge = {
                        if (waiting != null) {
                            Badge(
                                containerColor = AppTheme.colors.accent,
                                contentColor = AppTheme.colors.onAccent,
                                modifier = Modifier.semantics { contentDescription = waiting },
                            ) { Text(placesCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Apps, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.places_title), maxLines = 1) },
            colors = colors,
        )
    }
}
