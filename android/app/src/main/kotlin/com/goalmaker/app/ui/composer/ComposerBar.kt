package com.goalmaker.app.ui.composer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The chat-style composer (docs/design/spec.md): a floating pill that grows a preview of what the
 * line will save as you type. Enter or Send saves; tapping a chip removes its part of the line.
 * The caller places it above the keyboard or the navigation bar.
 */
@Composable
fun ComposerBar(
    state: TextFieldState,
    chips: List<ComposerChip>,
    canSend: Boolean,
    onSubmit: () -> Unit,
    onRemove: (ComposerChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(if (chips.isEmpty()) 28.dp else 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .animateContentSize(),
    ) {
        Column {
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                ) {
                    chips.forEach { chip -> PreviewChip(chip, onRemove) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                TextField(
                    state = state,
                    placeholder = { Text(stringResource(R.string.today_composer_placeholder)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    onKeyboardAction = { onSubmit() },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = onSubmit, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.today_add))
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(chip: ComposerChip, onRemove: (ComposerChip) -> Unit) {
    val area = chip.areaColorId?.let { id -> AppTheme.areaColors.firstOrNull { it.id == id } }
    val label = listOfNotNull(chip.label, chip.note).joinToString(" · ")
    InputChip(
        selected = false,
        onClick = { onRemove(chip) },
        label = { Text(label) },
        leadingIcon = {
            if (chip.kind == SpanKind.AREA) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(area?.let { AppTheme.colors.areaContent(it) } ?: MaterialTheme.colorScheme.outline, CircleShape),
                )
            } else {
                Icon(icon(chip.kind), contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
            }
        },
        trailingIcon = {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.composer_remove, chip.label),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        modifier = if (chip.muted) Modifier.alpha(0.7f) else Modifier,
    )
}

private fun icon(kind: SpanKind): ImageVector = when (kind) {
    SpanKind.DATE -> Icons.Outlined.Event
    SpanKind.TIME -> Icons.Outlined.Schedule
    SpanKind.REPEAT -> Icons.Outlined.Repeat
    SpanKind.TAG -> Icons.Outlined.Tag
    SpanKind.PRIORITY -> Icons.Outlined.Flag
    SpanKind.PROJECT -> Icons.Outlined.Folder
    SpanKind.IDEA -> Icons.Outlined.Lightbulb
    SpanKind.COMMAND -> Icons.Outlined.Terminal
    SpanKind.AREA -> Icons.Outlined.Event
}
