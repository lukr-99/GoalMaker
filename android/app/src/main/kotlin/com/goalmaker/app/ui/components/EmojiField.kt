package com.goalmaker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The emoji of a habit, a goal or an area: the one chosen sits in a box that opens a picker of the
 * ones people reach for, and anything else can still be typed in.
 */
@Composable
fun EmojiField(emoji: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val label = stringResource(R.string.emoji_pick)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppTheme.colors.surface)
                .clickable { picking = true }
                .semantics { contentDescription = label },
        ) {
            if (emoji.isEmpty()) {
                Text("🙂", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textMuted)
            } else {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(R.string.emoji_label), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.accent)
            TextButton(onClick = { picking = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(if (emoji.isEmpty()) R.string.emoji_pick else R.string.emoji_change))
            }
        }
    }

    if (picking) {
        EmojiPicker(
            onPick = { picked ->
                onChange(picked)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The picker itself: a few rows of emoji by theme, a way to clear it, and a box to type any other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emoji_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EMOJI.forEach { (group, emojis) ->
                    Text(
                        stringResource(group),
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.colors.accent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        emojis.forEach { emoji ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(emoji) }
                                    .semantics { contentDescription = emoji },
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(16) },
                    label = { Text(stringResource(R.string.emoji_own)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = typed.isNotBlank(), onClick = { onPick(typed.trim()) }) {
                Text(stringResource(R.string.emoji_use))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick("") }) { Text(stringResource(R.string.emoji_none)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel)) }
            }
        },
    )
}

// The emoji people reach for, by what they are usually about.
private val EMOJI: List<Pair<Int, List<String>>> = listOf(
    R.string.emoji_group_health to listOf("💧", "🏃", "🚶", "🚴", "🏋️", "🧘", "🥗", "🍎", "😴", "🦷", "💊", "🧴"),
    R.string.emoji_group_mind to listOf("📖", "📝", "🧠", "🎧", "🎵", "🎸", "🎨", "📷", "🌱", "🧩", "♟️", "🗣️"),
    R.string.emoji_group_work to listOf("💻", "📊", "📌", "📅", "✉️", "📞", "🧹", "🛠️", "💰", "🧾", "🚀", "🎯"),
    R.string.emoji_group_life to listOf("🏠", "👨‍👩‍👧", "🐕", "🌍", "☀️", "🌙", "🔥", "⭐", "❤️", "🙏", "🍳", "🛒"),
)
