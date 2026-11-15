package com.goalmaker.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The page's opening, on a hero card like the big-number cards: the mark drawing its arrow, the name
 * in the theme's heading face, what GoalMaker is, and the version as the big number. A trend line
 * climbs behind it all in the hero's accent.
 */
@Composable
fun AboutHero(info: AppInfo, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(colors.hero),
    ) {
        TrendStroke(
            colors.heroAccent.copy(alpha = 0.28f),
            Modifier.align(Alignment.BottomEnd).width(220.dp).height(150.dp),
            width = 10.dp,
            delayMillis = AppTheme.motion.standard,
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GoalMakerLogo(size = 56.dp, intro = true)
                Text(
                    AppTheme.headline(stringResource(R.string.app_name)),
                    style = AppTheme.type.heading.copy(fontSize = 32.sp, lineHeight = 36.sp),
                    color = colors.onHero,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onHero,
                modifier = Modifier.padding(top = 14.dp),
            )
            AboutLabel(stringResource(R.string.about_version_label), colors.onHero, Modifier.padding(top = 22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    info.versionName,
                    style = AppTheme.type.number.copy(fontSize = 52.sp, lineHeight = 56.sp),
                    color = colors.heroAccent,
                )
                if (info.isDevBuild) {
                    Text(
                        stringResource(R.string.about_dev_build),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onHero,
                        modifier = Modifier
                            .border(1.dp, colors.onHero, AppTheme.shapes.button)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
