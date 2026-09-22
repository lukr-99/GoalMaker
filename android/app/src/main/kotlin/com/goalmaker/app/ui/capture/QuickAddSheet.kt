package com.goalmaker.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import kotlinx.coroutines.launch

/**
 * The quick-add sheet the widget opens: the composer on its own over whatever the owner was doing,
 * with the keyboard already up. A line with no day lands in the Inbox to be sorted later; the Today
 * chip plans it for today. Saving or tapping outside closes it.
 */
@Composable
fun QuickAddSheet(
    viewModel: CaptureViewModel,
    forToday: Boolean,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val line = rememberTextFieldState(if (forToday) TODAY_TOKEN else "")
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            val draft = viewModel.preview(line.text.toString())
            if (state.loaded && !state.signedIn) {
                // The sheet floats over the home screen with nothing but a dim behind it, so this
                // line carries its own surface rather than taking its chances on the wallpaper.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.share_signed_out),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // One tap for the day the widget is most often used for; the line keeps saying what happens.
            FilterChip(
                selected = draft.plannedDate == viewModel.today(),
                onClick = { line.toggleToday(viewModel) },
                label = { Text(stringResource(R.string.composer_date_today)) },
                modifier = Modifier.padding(start = 24.dp),
            )
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
                onSubmit = { scope.launch { if (viewModel.save(draft, notes = "")) onSaved() } },
                onRemove = { chip -> line.setTextAndPlaceCursorAtEnd(removeParts(line.text.toString(), chip.spans)) },
                modifier = Modifier.focusRequester(focus),
            )
        }
    }
}

private const val TODAY_TOKEN = "today "

// The chip writes its own word into the line, so the composer stays the one thing that says what is saved.
private fun androidx.compose.foundation.text.input.TextFieldState.toggleToday(viewModel: CaptureViewModel) {
    val current = text.toString()
    val spans = viewModel.preview(current).spans.filter { it.kind == SpanKind.DATE }
    val without = removeParts(current, spans)
    val planned = viewModel.preview(current).plannedDate == viewModel.today()
    setTextAndPlaceCursorAtEnd(if (planned) without else "$without $TODAY_TOKEN".trim() + " ")
}
