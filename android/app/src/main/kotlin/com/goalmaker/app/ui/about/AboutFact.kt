package com.goalmaker.app.ui.about

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/** One line about the owner's data, with a small accent icon in front. */
@Composable
fun AboutFact(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.padding(top = 1.dp, end = 12.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
    }
}
