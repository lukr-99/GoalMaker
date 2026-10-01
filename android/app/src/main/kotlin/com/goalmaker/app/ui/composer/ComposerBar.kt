package com.goalmaker.app.ui.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The chat-style composer (docs/design/spec.md): a floating pill that grows a preview of what the
 * line will save as you type. Enter or Send saves; tapping a chip removes its part of the line, unless
 * [onRemove] is null, when the chips only show what was read.
 * The caller places it above the keyboard or the navigation bar. [leading] sits before the text, where
 * the composer keeps its switch to chat; [placeholder] and [sendLabel] follow what a line does.
 *
 * With [onOpenForm] the round button also opens the page's full form (docs/composer.md, "The bottom
 * bar on every list"): it is a plus named [formLabel] while the line is empty, and turns into the send
 * arrow once something is typed ([BarButton]). While [chatting] it is always the send arrow. Without
 * [onOpenForm] it only sends, as the quick-add box and Plan tomorrow want.
 */
@Composable
fun ComposerBar(
    state: TextFieldState,
    chips: List<ComposerChip>,
    canSend: Boolean,
    onSubmit: () -> Unit,
    onRemove: ((ComposerChip) -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.today_composer_placeholder),
    sendLabel: String = stringResource(R.string.today_add),
    leading: (@Composable () -> Unit)? = null,
    onOpenForm: (() -> Unit)? = null,
    formLabel: String = "",
    chatting: Boolean = false,
) {
    val button = BarButton.of(state.text.toString(), chatting, hasForm = onOpenForm != null)
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
                    chips.forEach { chip -> if (onRemove != null) PreviewChip(chip, onRemove) else ReadChip(chip) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (leading != null) 6.dp else 0.dp, end = 8.dp),
            ) {
                leading?.invoke()
                TextField(
                    state = state,
                    placeholder = { Text(placeholder) },
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
                BarRoundButton(
                    button = button,
                    enabled = button == BarButton.PLUS || canSend,
                    label = if (button == BarButton.PLUS) formLabel else sendLabel,
                    onClick = { if (button == BarButton.PLUS) onOpenForm?.invoke() else onSubmit() },
                )
            }
        }
    }
}

/**
 * The round button: the plus spins out and shrinks while the arrow fades and grows in, and the shape
 * goes from a rounded square to a circle (the add prototype, v2). With reduce motion the icons only
 * cross-fade.
 */
@Composable
private fun BarRoundButton(button: BarButton, enabled: Boolean, label: String, onClick: () -> Unit) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val corner by animateIntAsState(
        targetValue = if (button == BarButton.PLUS) PLUS_CORNER else CIRCLE,
        animationSpec = if (reduced) snap() else tween(motion.standard),
        label = "bar button shape",
    )
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        AnimatedContent(
            targetState = button,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(motion.quick)) togetherWith fadeOut(tween(motion.quick))
                } else {
                    (fadeIn(tween(motion.quick)) + scaleIn(tween(motion.standard), initialScale = 0.5f)) togetherWith
                        (fadeOut(tween(motion.quick)) + scaleOut(tween(motion.standard), targetScale = 0.4f))
                }
            },
            label = "bar button",
        ) { shown ->
            // The plus turns as it leaves and as it comes back; the arrow only grows.
            val turn by transition.animateFloat(
                transitionSpec = { if (reduced) snap() else tween(motion.standard) },
                label = "bar button turn",
            ) { phase ->
                when {
                    reduced || shown == BarButton.SEND || phase == EnterExitState.Visible -> 0f
                    phase == EnterExitState.PreEnter -> -PLUS_TURN
                    else -> PLUS_TURN
                }
            }
            Icon(
                imageVector = if (shown == BarButton.PLUS) Icons.Rounded.Add else Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
    }
}

/** A chip that only shows what the line says (Wants, Habits, Goals); a [ComposerChip.warning] one is in the danger color. */
@Composable
private fun ReadChip(chip: ComposerChip) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (chip.warning) BorderStroke(1.dp, colors.danger.copy(alpha = 0.6f)) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(
                chip.icon ?: icon(chip.kind),
                contentDescription = null,
                tint = if (chip.warning) colors.danger else colors.accent,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
            Text(
                listOfNotNull(chip.label, chip.note).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = if (chip.warning) colors.danger else colors.text,
            )
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
                Icon(chip.icon ?: icon(chip.kind), contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
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

// The plus's corners as a share of its size (a rounded square), the arrow's (a circle), and how far
// the plus turns as it goes.
private const val PLUS_CORNER = 33
private const val CIRCLE = 50
private const val PLUS_TURN = 135f
