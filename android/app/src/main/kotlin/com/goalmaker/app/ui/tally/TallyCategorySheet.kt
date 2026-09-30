package com.goalmaker.app.ui.tally

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.ui.components.EmojiField
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Adds a Tally category of the owner's own, or edits or deletes [initial] (docs/tally.md): a name, an
 * optional emoji and a color from the area palette, a new one starting at the first color no category
 * wears yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TallyCategorySheet(viewModel: TallyViewModel, initial: TallyCategory?, taken: Set<String>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val palette = AppTheme.areaColors.map { it.id }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji.orEmpty()) }
    var color by rememberSaveable {
        mutableStateOf(initial?.color ?: palette.firstOrNull { it !in taken } ?: palette.firstOrNull().orEmpty())
    }
    val canSave = name.isNotBlank() && color.isNotEmpty()

    fun save() {
        if (!canSave) return
        scope.launch {
            val saved = if (initial == null) viewModel.addCategory(name, color, emoji) != null else viewModel.updateCategory(initial.id, name, color, emoji)
            if (saved) sheet.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            ScreenTitle(stringResource(if (initial == null) R.string.tally_category_add else R.string.tally_category_edit))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.tally_category_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            EmojiField(emoji = emoji, onChange = { emoji = it })
            Text(stringResource(R.string.tally_category_color), style = MaterialTheme.typography.titleSmall)
            val locale = LocalConfiguration.current.locales[0]
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { id ->
                    val description = id.replaceFirstChar { it.titlecase(locale) }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .border(if (id == color) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { color = id }
                            .semantics {
                                contentDescription = description
                                selected = id == color
                            },
                    ) {
                        Box(Modifier.size(32.dp).background(tallyColor(id), CircleShape))
                        if (id == color) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Row {
                initial?.let { category ->
                    TextButton(onClick = {
                        viewModel.deleteCategory(category.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.tally_delete), color = AppTheme.colors.danger) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.tally_save)) }
            }
        }
    }
}
