package com.goalmaker.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.assistant.ChatRole
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The short chat thread above the composer: the owner's lines on the right, the answers on the left,
 * a thinking indicator while a request runs, and a button that clears it all. A line that got no
 * answer says why, with a way to send it again while chat can run.
 */
@Composable
fun ChatThread(state: ChatUiState, onClear: () -> Unit, onRetry: (Long) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val count = state.lines.size + if (state.thinking) 1 else 0
    // The newest line is the one to see.
    LaunchedEffect(count) { if (count > 0) list.animateScrollToItem(count - 1) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .animateContentSize(),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.colors.accent,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.chat_forgotten),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.ClearAll, contentDescription = stringResource(R.string.chat_clear))
                }
            }
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = THREAD_HEIGHT.dp),
            ) {
                items(state.lines, key = ChatLine::id) { line ->
                    Line(line, canRetry = state.chatting && !state.thinking, onRetry = { onRetry(line.id) })
                }
                if (state.thinking) item(key = "thinking") { Thinking() }
            }
        }
    }
}

/** Says why the composer adds tasks although the owner picked chat. */
@Composable
fun ChatUnavailableNote(availability: ChatAvailability, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(18.dp))
        Text(unavailableText(availability), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun Line(line: ChatLine, canRetry: Boolean, onRetry: () -> Unit) {
    val mine = line.message.role == ChatRole.USER
    val spoken = stringResource(if (mine) R.string.chat_line_owner else R.string.chat_line_answer, line.message.text)
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = BUBBLE_WIDTH.dp)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    // A new answer is read out as it arrives.
                    if (!mine) liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(line.message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        line.problem?.let { problem ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = problemText(problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (canRetry && problem.retryable) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                }
            }
        }
    }
}

@Composable
private fun Thinking() {
    val label = stringResource(R.string.chat_thinking)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun problemText(problem: ChatProblem): String = stringResource(
    when (problem) {
        ChatProblem.OFFLINE -> R.string.chat_problem_offline
        ChatProblem.SIGNED_OUT -> R.string.chat_problem_signed_out
        ChatProblem.NOT_SET_UP -> R.string.chat_problem_not_set_up
        ChatProblem.RATE_LIMITED -> R.string.chat_problem_rate_limited
        ChatProblem.PROVIDER_LIMIT -> R.string.chat_problem_provider_limit
        ChatProblem.BAD_REQUEST -> R.string.chat_problem_bad_request
        ChatProblem.FAILED -> R.string.chat_problem_failed
    },
)

// A signed-out or unset server won't take the line any better a second time.
private val ChatProblem.retryable: Boolean
    get() = this != ChatProblem.SIGNED_OUT && this != ChatProblem.NOT_SET_UP && this != ChatProblem.BAD_REQUEST

// Enough for a few lines before the list starts to scroll, so the lists behind stay in view.
private const val THREAD_HEIGHT = 260

private const val BUBBLE_WIDTH = 300
