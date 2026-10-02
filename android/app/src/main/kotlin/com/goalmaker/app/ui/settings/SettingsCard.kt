package com.goalmaker.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.domain.settings.SectionHint
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * One Settings section as a card: its title, a one-line description, then its rows. When [hint]
 * is for this section the card lights up (docs/design/spec.md, Settings): one [Animatable] from 0
 * to 1 and back drives the tint, the 2 dp inner ring, the 4 dp soft glow, the 3 dp left edge bar
 * and the title color, all drawn behind the content. [titleFocus] lets a jump move focus (and
 * TalkBack) to the title.
 */
@Composable
fun SettingsCard(
    section: SettingsSection,
    hint: HintRequest<SettingsSection>?,
    titleFocus: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val highlight = colors.highlight
    val shape = AppTheme.shapes.card
    val level = remember { Animatable(0f) }
    val mine = hint?.takeIf { it.section == section }
    val plan: SectionHint? = mine?.plan
    LaunchedEffect(mine?.serial) {
        level.snapTo(0f)
        val playing = mine?.plan ?: return@LaunchedEffect
        if (playing.riseMillis == 0) level.snapTo(1f) else level.animateTo(1f, tween(playing.riseMillis, easing = LinearOutSlowInEasing))
        delay(playing.holdMillis.toLong())
        if (playing.fadeMillis == 0) level.snapTo(0f) else level.animateTo(0f, tween(playing.fadeMillis, easing = FastOutSlowInEasing))
    }
    val ringWidth = 2.dp
    val glowWidth = 4.dp
    val edgeWidth = 3.dp
    val edgeInset = 14.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val amount = level.value
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                // The soft glow spreads 4 dp outside; the fill covers its inner half.
                if (plan?.glow == true && amount > 0f) {
                    drawPath(path, highlight.glow.copy(alpha = highlight.glow.alpha * amount), style = Stroke(width = glowWidth.toPx() * 2))
                }
                drawPath(path, colors.surface)
                if (plan?.tint == true && amount > 0f) {
                    drawPath(path, highlight.tint.copy(alpha = amount))
                }
                if (plan?.ring == true && amount > 0f) {
                    clipPath(path) {
                        drawPath(path, highlight.ring.copy(alpha = highlight.ring.alpha * amount), style = Stroke(width = ringWidth.toPx() * 2))
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val grow = (plan?.edge ?: 0f) * level.value
                if (grow > 0f) {
                    val full = size.height - edgeInset.toPx() * 2
                    val height = full * grow
                    drawRoundRect(
                        color = highlight.edge.copy(alpha = level.value.coerceIn(0f, 1f)),
                        topLeft = Offset(4.dp.toPx(), (size.height - height) / 2),
                        size = Size(edgeWidth.toPx(), height),
                        cornerRadius = CornerRadius(edgeWidth.toPx() / 2),
                    )
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val resting = colors.text
        val titleColor = if (plan?.title == true) lerp(resting, highlight.title, level.value) else resting
        Text(
            AppTheme.headline(stringResource(section.title)),
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            modifier = Modifier
                .semantics { heading() }
                .focusRequester(titleFocus)
                .focusable(),
        )
        Text(
            stringResource(section.description),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        content()
    }
}
