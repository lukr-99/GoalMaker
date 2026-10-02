package com.goalmaker.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * The Areas and tags section of Settings: the areas in use with their colors, the tags, and a link
 * row to the full manager, where they are added, renamed, recolored, reordered and archived. An
 * empty list says in one muted line how to fill it.
 */
@Composable
fun AreasAndTagsCard(areas: List<AreaItem>, tags: List<TagItem>, onOpenAreas: () -> Unit) {
    PillsRow(stringResource(R.string.areas_areas), empty = stringResource(R.string.areas_none).takeIf { areas.isEmpty() }) {
        areas.forEach { area -> AreaPill(area) }
    }
    RowDivider()
    PillsRow(stringResource(R.string.areas_tags), empty = stringResource(R.string.areas_no_tags).takeIf { tags.isEmpty() }) {
        tags.forEach { tag -> Pill { Text("#${tag.name}", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.text) } }
    }
    RowDivider()
    LinkRow(
        title = stringResource(R.string.areas_open),
        hint = stringResource(R.string.areas_open_hint),
        external = false,
        onClick = onOpenAreas,
    )
}

// A label over a wrap of pills, or the one muted line that says how to add the first.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillsRow(title: String, empty: String?, pills: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
        if (empty != null) {
            Text(empty, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { pills() }
        }
    }
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
