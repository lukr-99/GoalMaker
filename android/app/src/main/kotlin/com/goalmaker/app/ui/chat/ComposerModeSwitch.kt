package com.goalmaker.app.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.goalmaker.app.R
import com.goalmaker.app.domain.settings.ComposerMode

/**
 * The composer's switch between quick-add and chat, at the start of the pill. It shows the mode the
 * owner picked; when chat can't run, [ChatUnavailableNote] above the pill says why, and TalkBack
 * reads the reason with the switch.
 */
@Composable
fun ComposerModeSwitch(state: ChatUiState, onChoose: (ComposerMode) -> Unit) {
    val chat = state.chosen == ComposerMode.CHAT
    val name = stringResource(R.string.chat_mode)
    val status = when {
        state.chatBlocked -> unavailableText(state.availability)
        chat -> stringResource(R.string.chat_mode_on)
        else -> stringResource(R.string.chat_mode_off)
    }
    val hint = stringResource(if (chat) R.string.chat_switch_to_quick_add else R.string.chat_switch_to_chat)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconToggleButton(
            checked = chat,
            onCheckedChange = { on -> onChoose(if (on) ComposerMode.CHAT else ComposerMode.QUICK_ADD) },
            modifier = Modifier.semantics {
                contentDescription = name
                stateDescription = status
            },
        ) {
            Icon(
                imageVector = if (chat) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                contentDescription = null,
            )
        }
    }
}

/** Why chat can't run, and that the composer adds tasks meanwhile. */
@Composable
internal fun unavailableText(availability: ChatAvailability): String = stringResource(
    when (availability) {
        ChatAvailability.OFFLINE -> R.string.chat_unavailable_offline
        ChatAvailability.SIGNED_OUT -> R.string.chat_unavailable_signed_out
        ChatAvailability.NOT_SET_UP -> R.string.chat_unavailable_not_set_up
        // Never shown: nothing is blocked.
        ChatAvailability.AVAILABLE -> R.string.chat_mode_on
    },
)
