package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.QuickAddActivity
import com.goalmaker.app.R

/**
 * The quick-add widget: one tap on the home screen opens the composer over whatever is on screen,
 * without the app coming up. The pill files what is typed the way the composer does, so a line with
 * no day waits in the Inbox to be sorted later; Today plans it for today.
 *
 * A home screen widget cannot hold a text field of its own (a widget is drawn by the launcher, which
 * has no keyboard), so the typing happens in [QuickAddActivity], which the widget starts.
 */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val skin = WidgetSkin.of(context)
        provideContent {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(skin.background))
                    .cornerRadius(24.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clickable(actionStartActivity(QuickAddActivity.intent(context, forToday = false))),
            ) {
                Text(
                    context.getString(R.string.widget_quick_add_hint),
                    style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Today(skin, context)
            }
        }
    }

    @Composable
    private fun Today(skin: WidgetSkin, context: Context) {
        Text(
            context.getString(R.string.widget_quick_add_today),
            style = TextStyle(
                color = ColorProvider(Color(skin.background)),
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier
                .background(Color(skin.accent))
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(actionStartActivity(QuickAddActivity.intent(context, forToday = true))),
        )
    }
}
