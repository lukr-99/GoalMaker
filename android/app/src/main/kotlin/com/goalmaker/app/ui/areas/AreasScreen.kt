package com.goalmaker.app.ui.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The areas and tags manager (M2-11). A tap on an area or a tag edits it; the arrows reorder the
 * areas, which is the order the filter and the pickers use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(viewModel: AreasViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // null: no dialog; an empty id: a new one.
    var editingArea by remember { mutableStateOf<AreaItem?>(null) }
    var editingTag by remember { mutableStateOf<TagItem?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(R.string.areas_title))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { Heading(stringResource(R.string.areas_areas)) }
            itemsIndexed(state.active, key = { _, area -> area.id }) { index, area ->
                AreaRow(
                    area = area,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.active.lastIndex,
                    onEdit = { editingArea = area },
                    onMove = { by -> viewModel.moveArea(area.id, by) },
                )
            }
            item { AddButton(stringResource(R.string.areas_add_area)) { editingArea = AreaItem("", "", state.palette.first(), null) } }
            if (state.archived.isNotEmpty()) {
                item { Heading(stringResource(R.string.areas_archived)) }
                itemsIndexed(state.archived, key = { _, area -> area.id }) { _, area ->
                    ArchivedAreaRow(area, onEdit = { editingArea = area }, onRestore = { viewModel.restoreArea(area.id) })
                }
            }
            item { Heading(stringResource(R.string.areas_tags)) }
            if (state.tags.isEmpty()) {
                item { Text(stringResource(R.string.areas_no_tags), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted) }
            }
            itemsIndexed(state.tags, key = { _, tag -> tag.id }) { _, tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppTheme.density.rowMinHeight.dp)
                        .background(AppTheme.colors.surface, AppTheme.shapes.row)
                        .clickable { editingTag = tag }
                        .padding(horizontal = 16.dp),
                ) {
                    Text("#${tag.name}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { AddButton(stringResource(R.string.areas_add_tag)) { editingTag = TagItem("", "") } }
        }
    }

    editingArea?.let { area ->
        AreaDialog(
            area = area,
            palette = state.palette,
            onSave = { name, emoji, color -> viewModel.saveArea(area.id.ifEmpty { null }, name, emoji, color) },
            onDelete = if (area.id.isEmpty()) null else ({ viewModel.deleteArea(area.id) }),
            onArchive = when {
                area.id.isEmpty() -> null
                area.archived -> ({ viewModel.restoreArea(area.id) })
                else -> ({ viewModel.archiveArea(area.id) })
            },
            onDismiss = { editingArea = null },
        )
    }
    editingTag?.let { tag ->
        TagDialog(
            tag = tag,
            onSave = { name -> viewModel.saveTag(tag.id.ifEmpty { null }, name) },
            onDelete = if (tag.id.isEmpty()) null else ({ viewModel.deleteTag(tag.id) }),
            onDismiss = { editingTag = null },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AreaRow(area: AreaItem, canMoveUp: Boolean, canMoveDown: Boolean, onEdit: () -> Unit, onMove: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Swatch(area.colorId, size = 14)
        Text(
            listOfNotNull(area.emoji, area.name).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.areas_move_up, area.name))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.areas_move_down, area.name))
        }
    }
}

/** An archived area: muted, with Restore, and a tap to edit or delete it. */
@Composable
private fun ArchivedAreaRow(area: AreaItem, onEdit: () -> Unit, onRestore: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Swatch(area.colorId, size = 14)
        Text(
            listOfNotNull(area.emoji, area.name).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        TextButton(onClick = onRestore) { Text(stringResource(R.string.areas_restore)) }
    }
}

@Composable
private fun Swatch(colorId: String, size: Int) {
    val palette = AppTheme.areaColors.firstOrNull { it.id == colorId }
    Box(Modifier.size(size.dp).background(palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.outline, CircleShape))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaDialog(
    area: AreaItem,
    palette: List<String>,
    onSave: suspend (String, String, String) -> Boolean,
    onDelete: (() -> Unit)?,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(area.name) }
    var emoji by remember { mutableStateOf(area.emoji.orEmpty()) }
    var color by remember { mutableStateOf(area.colorId) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (area.id.isEmpty()) R.string.areas_new_area else R.string.areas_edit_area)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        refused = false
                    },
                    label = { Text(stringResource(R.string.areas_name)) },
                    isError = refused,
                    supportingText = if (refused) ({ Text(stringResource(R.string.areas_name_taken)) }) else null,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(16) },
                    label = { Text(stringResource(R.string.areas_emoji)) },
                    singleLine = true,
                )
                val locale = LocalConfiguration.current.locales[0]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { id ->
                        val description = id.replaceFirstChar { it.titlecase(locale) }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .border(if (id == color) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .padding(4.dp)
                                .clickable(role = Role.RadioButton) { color = id }
                                .semantics {
                                    contentDescription = description
                                    selected = id == color
                                },
                        ) {
                            Swatch(id, size = 28)
                            if (id == color) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { if (onSave(name, emoji, color)) onDismiss() else refused = true } }) {
                Text(stringResource(R.string.areas_save))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = {
                        delete()
                        onDismiss()
                    }) { Text(stringResource(R.string.areas_delete), color = AppTheme.colors.danger) }
                }
                onArchive?.let { archive ->
                    TextButton(onClick = {
                        archive()
                        onDismiss()
                    }) { Text(stringResource(if (area.archived) R.string.areas_restore else R.string.areas_archive)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.areas_cancel)) }
            }
        },
    )
}

@Composable
private fun TagDialog(tag: TagItem, onSave: suspend (String) -> Boolean, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(tag.name) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (tag.id.isEmpty()) R.string.areas_new_tag else R.string.areas_edit_tag)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.removePrefix("#")
                    refused = false
                },
                label = { Text(stringResource(R.string.areas_name)) },
                isError = refused,
                supportingText = if (refused) ({ Text(stringResource(R.string.areas_name_taken)) }) else null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { if (onSave(name)) onDismiss() else refused = true } }) {
                Text(stringResource(R.string.areas_save))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = {
                        delete()
                        onDismiss()
                    }) { Text(stringResource(R.string.areas_delete), color = AppTheme.colors.danger) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.areas_cancel)) }
            }
        },
    )
}
