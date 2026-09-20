package com.goalmaker.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.domain.share.SharedCapture
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import kotlinx.coroutines.launch

/**
 * The sheet another app's share opens (spec, story 10): the shared text as a composer line with its
 * live preview, the link it came from underneath, and the areas and projects it can go to. Saving
 * with nothing chosen leaves it in the Inbox.
 */
@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    capture: SharedCapture,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val line = rememberTextFieldState(capture.line)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.share_title)) }) },
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                if (capture.notes.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.share_notes), style = MaterialTheme.typography.labelMedium)
                            Text(
                                capture.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (state.projects.isNotEmpty()) {
                    Picker(
                        title = stringResource(R.string.share_project),
                        names = state.projects.map { it.name },
                        chosen = viewModel.preview(line.text.toString()).project,
                        onPick = { name -> line.pick(SpanKind.PROJECT, "+", name, viewModel) },
                    )
                }
                if (state.areas.isNotEmpty()) {
                    Picker(
                        title = stringResource(R.string.share_area),
                        names = state.areas.filterNot { it.archived }.map { it.name },
                        chosen = viewModel.preview(line.text.toString()).area,
                        onPick = { name -> line.pick(SpanKind.AREA, "@", name, viewModel) },
                    )
                }
                if (state.loaded && !state.signedIn) {
                    Text(stringResource(R.string.share_signed_out), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenApp) { Text(stringResource(R.string.share_open_app)) }
                }
            }

            val draft = viewModel.preview(line.text.toString())
            ComposerBar(
                state = line,
                chips = composerChips(
                    line.text.toString(),
                    draft,
                    viewModel.today(),
                    state.areas,
                    state.tagNames,
                    state.projects,
                ),
                canSend = state.signedIn && draft.title.isNotBlank(),
                onSubmit = {
                    scope.launch { if (viewModel.save(draft, capture.notes)) onSaved() }
                },
                onRemove = { chip -> line.setTextAndPlaceCursorAtEnd(removeParts(line.text.toString(), chip.spans)) },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.share_cancel)) }
            }
        }
    }
}

/** One row of choices: the names to pick from, with the chosen one selected. Picking again clears it. */
@Composable
private fun Picker(title: String, names: List<String>, chosen: String?, onPick: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { name ->
                val selected = chosen?.equals(name, ignoreCase = true) == true
                FilterChip(
                    selected = selected,
                    onClick = { onPick(if (selected) null else name) },
                    label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

// Picking sets the line's own token, so the composer stays the one thing that says what is saved.
private fun TextFieldState.pick(kind: SpanKind, marker: String, name: String?, viewModel: ShareViewModel) {
    val current = text.toString()
    val without = removeParts(current, viewModel.preview(current).spans.filter { it.kind == kind })
    val token = name?.let { " $marker${it.trim().replace(' ', '_')}" }.orEmpty()
    setTextAndPlaceCursorAtEnd((without + token).trim())
}
