package com.goalmaker.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Narrows every list to an area and/or a tag (docs/lists.md, spec story 9). Each chip opens a menu;
 * a chosen filter shows its name and an x that clears it. Nothing shows when there are no areas or
 * tags to filter by.
 */
@Composable
fun ListFilterRow(
    filter: ListFilter,
    areas: List<AreaItem>,
    tags: List<TagItem>,
    onArea: (String?) -> Unit,
    onTag: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (areas.isEmpty() && tags.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.density.pagePadding.dp),
    ) {
        if (areas.isNotEmpty()) {
            val chosen = areas.firstOrNull { it.id == filter.areaId }
            PickerChip(
                label = chosen?.let { area -> listOfNotNull(area.emoji, area.name).joinToString(" ") }
                    ?: stringResource(R.string.filter_all_areas),
                selected = chosen != null,
                onClear = { onArea(null) },
                choices = areas.map { area -> area.id to listOfNotNull(area.emoji, area.name).joinToString(" ") },
                dot = { id -> areas.firstOrNull { it.id == id }?.let { AreaDot(it) } },
                onChoose = onArea,
            )
        }
        if (tags.isNotEmpty()) {
            val chosen = tags.firstOrNull { it.id == filter.tagId }
            PickerChip(
                label = chosen?.let { "#${it.name}" } ?: stringResource(R.string.filter_all_tags),
                selected = chosen != null,
                onClear = { onTag(null) },
                choices = tags.map { it.id to "#${it.name}" },
                dot = { null },
                onChoose = onTag,
            )
        }
    }
}

@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    onClear: () -> Unit,
    choices: List<Pair<String, String>>,
    dot: @Composable (String) -> Unit?,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { if (selected) onClear() else open = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    if (selected) Icons.Outlined.Close else Icons.Outlined.ArrowDropDown,
                    contentDescription = if (selected) stringResource(R.string.filter_clear) else null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { dot(id) },
                    onClick = {
                        open = false
                        onChoose(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AreaDot(area: AreaItem) {
    val palette = AppTheme.areaColors.firstOrNull { it.id == area.colorId }
    Box(Modifier.size(10.dp).background(palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.outline, CircleShape))
}
