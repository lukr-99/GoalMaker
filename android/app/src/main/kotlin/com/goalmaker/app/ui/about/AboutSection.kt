package com.goalmaker.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A section of the About page: a surface card with a short accent bar beside its title, the way the
 * Settings cards mark the section being read, then what it holds.
 */
@Composable
fun AboutSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(colors.surface)
            .padding(AppTheme.density.cardPadding.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(bottom = 10.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(AppTheme.shapes.checkbox).background(colors.accent))
            Text(
                AppTheme.headline(title),
                style = AppTheme.type.heading.copy(fontSize = 22.sp, lineHeight = 26.sp),
                color = colors.text,
                modifier = Modifier.padding(start = 10.dp).semantics { heading() },
            )
        }
        content()
    }
}
