package com.goalmaker.app.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Today's habits as a compact row of rings (design spec, Today). A tap checks in, a long press opens
 * the Habits screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRingsRow(rows: List<HabitRow>, onTap: (HabitRow) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier,
    ) {
        items(rows, key = { it.habit.id }) { row ->
            val label = stringResource(R.string.habits_check_in, row.habit.name)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(role = Role.Button, onClick = { onTap(row) }, onLongClick = onOpen)
                    .semantics { contentDescription = label }
                    .padding(vertical = 8.dp),
            ) {
                HabitRing(fraction = (row.ring ?: 0.0).toFloat(), size = 48.dp) {
                    when {
                        row.habit.emoji != null -> Text(row.habit.emoji, style = MaterialTheme.typography.titleMedium)
                        row.done -> Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(22.dp))
                    }
                }
                Text(
                    row.habit.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.done) AppTheme.colors.textMuted else AppTheme.colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
