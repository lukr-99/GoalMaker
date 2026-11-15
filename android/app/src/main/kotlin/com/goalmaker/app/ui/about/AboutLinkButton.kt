package com.goalmaker.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * One way to reach the author as a button: an icon, the place's name with the handle under it and the
 * ↗ that says it leaves the app. [filled] buttons take the primary color, the others an outline.
 * A screen reader hears the name, the handle and [actionLabel] as one button.
 */
@Composable
fun AboutLinkButton(
    icon: ImageVector,
    label: String,
    handle: String,
    actionLabel: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.button
    val content = if (filled) colors.onPrimary else colors.text
    val back = if (filled) colors.primary else Color.Transparent
    val edge = if (filled) colors.primary else colors.outline
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(back)
            .border(1.5.dp, edge, shape)
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label, $handle"
                role = Role.Button
                onClick(label = actionLabel) {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = content)
            Text(handle, style = MaterialTheme.typography.bodySmall, color = content)
        }
        Text("↗", style = MaterialTheme.typography.titleLarge, color = content)
    }
}
