package com.goalmaker.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * One choice of a small set (a goal's horizon, a habit's cadence): the chosen one carries a check and
 * the theme's accent, so it reads as chosen in every theme (design spec, color roles).
 */
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.colors.accent.copy(alpha = if (AppTheme.colors.isDark) 0.28f else 0.20f),
            selectedLabelColor = AppTheme.colors.text,
            selectedLeadingIconColor = AppTheme.colors.accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AppTheme.colors.outline,
            selectedBorderColor = AppTheme.colors.accent,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
        modifier = modifier,
    )
}
