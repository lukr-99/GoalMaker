package com.goalmaker.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.OngoingEvent
import com.goalmaker.app.ui.calendar.eventDays
import com.goalmaker.app.ui.theme.AppTheme

/**
 * An event going on today, as a slim line above Today's tasks (docs/calendar.md, Events): "Prague ·
 * day 2 of 4", or just the title for a one-day event, in its area's colour. A tap opens the event sheet.
 */
@Composable
internal fun TodayEventLine(ongoing: OngoingEvent, areas: List<AreaItem>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val event = ongoing.event
    val area = event.areaId?.let { id -> areas.firstOrNull { it.id == id } }
    val palette = area?.let { chosen -> AppTheme.areaColors.firstOrNull { it.id == chosen.colorId } }
    val text = if (ongoing.days > 1) stringResource(R.string.today_event_day, event.title, ongoing.dayOf, ongoing.days) else event.title
    val description = stringResource(R.string.calendar_event_span, text, eventDays(event.startsOn, event.endsOn))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .background(palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.accent, CircleShape),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The line's description says it with the event's days.
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
    }
}
