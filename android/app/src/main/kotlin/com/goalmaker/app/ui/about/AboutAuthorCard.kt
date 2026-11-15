package com.goalmaker.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.domain.about.AuthorLinks
import com.goalmaker.app.domain.about.Monogram
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The page's centrepiece: who made GoalMaker and how to reach them. A card ringed in the primary and
 * accent colors, a badge with the author's initials in the big-number face, the name in the heading
 * face, the invitation to give feedback, and the three links: Instagram and GitHub filled, the issues
 * page outlined. A small trend line climbs in the top corner.
 */
@Composable
fun AboutAuthorCard(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.card
    val uri = LocalUriHandler.current
    // A phone with no browser, or a locked-down profile, cannot open a link: the page then stays as it is.
    val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }
    val opens = stringResource(R.string.about_opens_in_browser)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(2.5.dp, Brush.linearGradient(listOf(colors.primary, colors.accent)), shape),
    ) {
        TrendStroke(
            colors.accent.copy(alpha = 0.55f),
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(72.dp, 52.dp),
            width = 4.dp,
            delayMillis = AppTheme.motion.emphasized,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(shape)
                    .background(colors.primary)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Monogram.of(AuthorLinks.NAME),
                    style = AppTheme.type.number.copy(fontSize = 30.sp, lineHeight = 32.sp),
                    color = colors.onPrimary,
                )
            }
            AboutLabel(stringResource(R.string.about_made_by), colors.accent, Modifier.padding(top = 16.dp))
            Text(
                AppTheme.headline(AuthorLinks.NAME),
                style = AppTheme.type.heading.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.about_author_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                AppTheme.headline(stringResource(R.string.about_feedback_heading)),
                style = AppTheme.type.heading.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.about_feedback_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val instagram = @Composable { m: Modifier ->
                    AboutLinkButton(
                        Icons.Outlined.PhotoCamera, stringResource(R.string.about_instagram), AuthorLinks.instagram.handle,
                        opens, filled = true, onClick = { open(AuthorLinks.instagram.url) }, modifier = m,
                    )
                }
                val github = @Composable { m: Modifier ->
                    AboutLinkButton(
                        Icons.Outlined.Code, stringResource(R.string.about_github), AuthorLinks.github.handle,
                        opens, filled = true, onClick = { open(AuthorLinks.github.url) }, modifier = m,
                    )
                }
                val wide = maxWidth >= SIDE_BY_SIDE
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            instagram(Modifier.weight(1f))
                            github(Modifier.weight(1f))
                        }
                    } else {
                        instagram(Modifier.fillMaxWidth())
                        github(Modifier.fillMaxWidth())
                    }
                    AboutLinkButton(
                        Icons.Outlined.BugReport, stringResource(R.string.about_issues), AuthorLinks.issues.handle,
                        opens, filled = false, onClick = { open(AuthorLinks.issues.url) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// Wide enough that each of the two profile buttons keeps its handle on one line.
private val SIDE_BY_SIDE = 440.dp
