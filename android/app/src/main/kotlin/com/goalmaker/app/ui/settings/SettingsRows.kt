package com.goalmaker.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.domain.settings.SettingsPageRules
import com.goalmaker.app.ui.theme.AppTheme

/*
 * The Settings row kit (docs/design/spec.md, Settings): every row is at least 56 dp high with a
 * title and a one-line hint, its control on the right or under it, and rows in a card are split by
 * thin dividers. Disabled rows fade to 45% and their hint says why.
 */

private val ROW_HEIGHT = 56.dp
private const val DISABLED_ALPHA = 0.45f

// The most of a row its control takes, so the title keeps room to read.
private const val CONTROL_SHARE = 0.6f

/** The thin line between two rows of a card. */
@Composable
fun RowDivider() {
    HorizontalDivider(thickness = Dp.Hairline, color = AppTheme.colors.outline.copy(alpha = 0.35f))
}

/** A row's title and its hint, which a result or a reason can replace. */
@Composable
private fun RowText(title: String, hint: String?, modifier: Modifier = Modifier, hintColor: Color? = null, titleColor: Color? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor ?: AppTheme.colors.text)
        if (!hint.isNullOrEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = hintColor ?: AppTheme.colors.textMuted)
        }
    }
}

/** A row with its control on the right: the title and hint fill the rest. */
@Composable
private fun SideRow(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hintColor: Color? = null,
    titleColor: Color? = null,
    control: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(
            title,
            hint,
            Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_ALPHA),
            hintColor,
            titleColor,
        )
        Row(
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA).atMost(CONTROL_SHARE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = control,
        )
    }
}

/**
 * Measures the content at most [share] of the width on offer. At the largest text sizes a row's
 * button would otherwise squeeze its title to a letter a line; this way the button's text wraps.
 */
private fun Modifier.atMost(share: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth * share).toInt()))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** A row whose control sits under the title, full width: segmented choices, sliders, fields, pickers. */
@Composable
private fun StackRow(title: String, hint: String?, saved: Int?, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = 10.dp).alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowText(title, hint, Modifier.weight(1f))
            SavedTick(saved)
        }
        content()
    }
}

/**
 * "✓ Saved" next to a control once its change is kept: in over 120 ms, held 1.5 s, out over 250 ms,
 * and said once to a screen reader. [count] goes up with every save; null or 0 shows nothing.
 */
@Composable
fun SavedTick(count: Int?) {
    val label = stringResource(R.string.settings_saved)
    val reduced = AppTheme.reduceMotion
    val shown = remember { Animatable(0f) }
    var said by remember { mutableStateOf("") }
    // The first count is what was saved before the screen opened, so it plays nothing.
    val first = remember { count }
    LaunchedEffect(count) {
        if (count == null || count == 0 || count == first) return@LaunchedEffect
        said = label
        if (reduced) shown.snapTo(1f) else shown.animateTo(1f, tween(SettingsPageRules.SAVED_IN_MILLIS))
        kotlinx.coroutines.delay(SettingsPageRules.SAVED_HOLD_MILLIS)
        if (reduced) shown.snapTo(0f) else shown.animateTo(0f, tween(SettingsPageRules.SAVED_OUT_MILLIS))
        said = ""
    }
    Box(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = said
        },
    ) {
        Text(
            stringResource(R.string.settings_saved_mark),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.accent,
            modifier = Modifier.alpha(shown.value).clearAndSetSemantics { },
        )
    }
}

/** On or off: the whole row is the switch, named by its title. */
@Composable
fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    saved: Int? = null,
    enabled: Boolean = true,
) {
    SideRow(
        title = title,
        hint = hint,
        enabled = enabled,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        SavedTick(saved)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Two to four short options, exactly one on. */
@Composable
fun <T> SegmentedRow(
    title: String,
    hint: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    saved: Int? = null,
) {
    StackRow(title, hint, saved) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (value, label) ->
                ToggleButton(
                    checked = selected == value,
                    onCheckedChange = { if (selected != value) onSelect(value) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                ) {
                    Text(label)
                }
            }
        }
    }
}

/** A visual pick, like the theme cards: the [content] draws the cards. */
@Composable
fun ChoiceCardsRow(title: String, saved: Int?, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
            SavedTick(saved)
            trailing()
        }
        content()
    }
}

/** Five options or more: the current one on a button that opens the list. */
@Composable
fun <T> DropdownRow(
    title: String,
    hint: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    saved: Int? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    SideRow(title = title, hint = hint) {
        SavedTick(saved)
        Box {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.semantics {
                    role = Role.DropdownList
                    contentDescription = title
                    stateDescription = current
                },
            ) {
                Text(current)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            open = false
                            if (value != selected) onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A free value that saves on Enter or when focus leaves. A refused value turns the border red,
 * says why under the field with an icon, and is never saved; the last good value stays in effect.
 */
@Composable
fun <V> TextFieldRow(
    title: String,
    hint: String,
    field: CommittedField<V>,
    error: String,
    onCommit: (V) -> Unit,
    saved: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    fun commit() = field.commit()?.let(onCommit)
    StackRow(title, hint, saved) {
        val message = if (field.invalid) {
            if (field.lastGood.isNotEmpty()) stringResource(R.string.settings_field_kept, error, field.lastGood) else error
        } else {
            null
        }
        OutlinedTextField(
            value = field.text,
            onValueChange = field::edit,
            singleLine = true,
            isError = field.invalid,
            label = { Text(title) },
            supportingText = message?.let {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(it)
                    }
                }
            },
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                commit()
                if (!field.invalid) focus.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) commit()
                    focused = state.isFocused
                }
                .semantics { if (message != null) error(message) },
        )
    }
}

/**
 * A range where the feel matters: it shows its value as it moves and saves once, on release.
 * [valueText] writes a position the way the row shows it.
 */
@Composable
fun SliderRow(
    title: String,
    hint: String?,
    value: Int,
    range: IntRange,
    valueText: (Int) -> String,
    onRelease: (Int) -> Unit,
    saved: Int? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(value.toFloat()) }
    if (!dragging && position != value.toFloat()) position = value.toFloat()
    val shown = position.toInt()
    StackRow("$title: ${valueText(shown)}", hint, saved) {
        Slider(
            value = position,
            onValueChange = {
                dragging = true
                position = it
            },
            onValueChangeFinished = {
                dragging = false
                val picked = Math.round(position)
                if (picked != value) onRelease(picked)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = valueText(Math.round(position))
            },
        )
    }
}

/**
 * An action with a result, like Export or Check for updates. While [busy], the button shows a
 * spinner and stays disabled; [result] replaces the hint, never adds lines under the row.
 */
@Composable
fun ButtonRow(
    title: String,
    hint: String?,
    button: String,
    onClick: () -> Unit,
    result: String? = null,
    trouble: Boolean = false,
    busy: Boolean = false,
    busyLabel: String? = null,
    enabled: Boolean = true,
    extra: @Composable RowScope.() -> Unit = {},
) {
    SideRow(
        title = title,
        hint = result ?: hint,
        hintColor = if (result != null && trouble) AppTheme.colors.danger else null,
        modifier = Modifier.semantics { if (result != null) liveRegion = LiveRegionMode.Polite },
    ) {
        extra()
        OutlinedButton(onClick = onClick, enabled = enabled && !busy) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
                Text(busyLabel ?: button, modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(button)
            }
        }
    }
}

/** Opens a page in the app (›) or outside it (↗): the whole row is the target. */
@Composable
fun LinkRow(title: String, hint: String?, external: Boolean, onClick: () -> Unit) {
    SideRow(
        title = title,
        hint = hint,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            if (external) "↗" else "›",
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** A read-only value the owner can select and copy. */
@Composable
fun InfoRow(title: String, value: String) {
    SideRow(title = title, hint = null) {
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        }
    }
}

/** The danger zone at the end of a section: an outlined block for the actions that can't be taken back. */
@Composable
fun DangerZone(content: @Composable ColumnScope.() -> Unit) {
    val danger = AppTheme.colors.danger
    androidx.compose.material3.Surface(
        shape = AppTheme.shapes.row,
        color = Color.Transparent,
        border = BorderStroke(1.dp, danger.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                AppTheme.headline(stringResource(R.string.settings_danger_zone)),
                style = MaterialTheme.typography.labelMedium,
                color = danger,
                modifier = Modifier.padding(top = 8.dp),
            )
            content()
        }
    }
}

/** A destructive action inside the danger zone; its button opens a confirm dialog. */
@Composable
fun DangerRow(
    title: String,
    hint: String,
    button: String,
    onClick: () -> Unit,
    result: String? = null,
    trouble: Boolean = false,
    enabled: Boolean = true,
) {
    val danger = AppTheme.colors.danger
    SideRow(
        title = title,
        hint = result ?: hint,
        titleColor = danger,
        hintColor = if (result != null && trouble) danger else null,
        modifier = Modifier.semantics { if (result != null) liveRegion = LiveRegionMode.Polite },
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            border = BorderStroke(1.dp, danger),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = danger),
        ) {
            Text(button)
        }
    }
}

/** Asks before a danger action: Cancel has the focus, so a stray Enter or tap away does nothing. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.danger),
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(stringResource(R.string.plan_cancel))
            }
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        },
    )
}
