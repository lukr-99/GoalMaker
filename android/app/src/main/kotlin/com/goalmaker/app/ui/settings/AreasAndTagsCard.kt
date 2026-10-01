package com.goalmaker.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The Areas and tags section of Settings: the areas in use with their colors, the tags, and the way
 * to the full manager, where they are added, renamed, recolored, reordered and archived.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreasAndTagsCard(areas: List<AreaItem>, tags: List<TagItem>, onOpenAreas: () -> Unit) {
    Text(
        stringResource(R.string.areas_open_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.areas_areas), style = MaterialTheme.typography.titleSmall)
    if (areas.isEmpty()) {
        Text(stringResource(R.string.areas_none), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            areas.forEach { area -> AreaPill(area) }
        }
    }
    Text(stringResource(R.string.areas_tags), style = MaterialTheme.typography.titleSmall)
    if (tags.isEmpty()) {
        Text(stringResource(R.string.areas_no_tags), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag -> Pill { Text("#${tag.name}", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.text) } }
        }
    }
    OutlinedButton(onClick = onOpenAreas) { Text(stringResource(R.string.areas_open)) }
}

// An area as the lists show it: its color, its emoji and its name.
@Composable
private fun AreaPill(area: AreaItem) {
    val palette = AppTheme.areaColors.firstOrNull { it.id == area.colorId }
    val content = palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.text
    Pill(fill = palette?.let { AppTheme.colors.areaContainer(it) } ?: AppTheme.colors.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(content, CircleShape))
            val label = listOfNotNull(area.emoji?.takeIf { it.isNotBlank() }, area.name).joinToString(" ")
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
private fun Pill(fill: Color = Color.Transparent, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(fill, RoundedCornerShape(50))
            .border(1.dp, if (fill == Color.Transparent) AppTheme.colors.outline else fill, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}
