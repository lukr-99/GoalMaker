package com.goalmaker.app.ui.habits

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/** Hide done on Today's habits and the Habits screen: on, it says Show done and the done habits go. */
@Composable
fun HideDoneChip(hiding: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = hiding,
        onClick = { onChange(!hiding) },
        label = { Text(stringResource(if (hiding) R.string.habits_show_done else R.string.habits_hide_done)) },
        leadingIcon = {
            Icon(
                if (hiding) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.colors.accent.copy(alpha = if (AppTheme.colors.isDark) 0.28f else 0.20f),
            selectedLabelColor = AppTheme.colors.text,
            selectedLeadingIconColor = AppTheme.colors.accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = hiding,
            borderColor = AppTheme.colors.outline,
            selectedBorderColor = AppTheme.colors.accent,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
        modifier = modifier,
    )
}
