package com.goalmaker.app.ui.about

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/** A section label as the design spec sets them: small, strong, upper case and spaced out. */
@Composable
fun AboutLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.12.em),
        color = color,
        modifier = modifier,
    )
}
