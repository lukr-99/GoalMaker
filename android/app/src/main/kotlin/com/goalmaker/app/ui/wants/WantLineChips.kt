package com.goalmaker.app.ui.wants

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.WantDraft
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.composer.ComposerChip

/**
 * The bottom bar's preview on Wants (docs/composer.md): the price, how long the want will wait
 * ([days], picked or from the price), and its reason, or a warning in the danger color while the
 * line has none. Nothing while the line is empty.
 */
@Composable
internal fun wantLineChips(line: String, draft: WantDraft, days: Int): List<ComposerChip> {
    if (line.isBlank()) return emptyList()
    val locale = LocalConfiguration.current.locales[0]
    val chips = mutableListOf<ComposerChip>()
    draft.price?.let { price ->
        chips += chip(WantMoney.format(price, draft.currency, locale), Icons.Outlined.Payments)
    }
    chips += chip(pluralStringResource(R.plurals.bar_want_waits, days, days), Icons.Outlined.HourglassEmpty)
    chips += if (draft.reason.isNotBlank()) {
        chip(stringResource(R.string.bar_want_why, draft.reason), Icons.Outlined.Lightbulb)
    } else {
        chip(stringResource(R.string.bar_want_no_why), Icons.Outlined.ErrorOutline, warning = true)
    }
    return chips
}

private fun chip(label: String, icon: ImageVector, warning: Boolean = false) =
    ComposerChip(SpanKind.IDEA, label, null, false, null, emptyList(), icon = icon, warning = warning)

