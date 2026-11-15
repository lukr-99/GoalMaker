package com.goalmaker.app.ui.about

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/** One credit: its name and what it does here, and its licence as a small outlined tag. */
@Composable
fun AboutCreditRow(credit: AboutCredit) {
    val colors = AppTheme.colors
    val role = stringResource(credit.role)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = "${credit.name}. $role. ${credit.licence}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(credit.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(role, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Text(
            credit.licence,
            style = MaterialTheme.typography.labelMedium,
            color = colors.accent,
            modifier = Modifier
                .border(1.dp, colors.outline, AppTheme.shapes.button)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
