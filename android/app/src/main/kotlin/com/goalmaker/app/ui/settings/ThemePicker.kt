package com.goalmaker.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.domain.design.ThemeDefinition
import com.goalmaker.app.domain.design.TypeStyle
import com.goalmaker.app.ui.theme.GoalMakerShapes
import com.goalmaker.app.ui.theme.ThemeFonts

/** The four themes as cards, each drawn in its own colors, fonts and corners (ADR 0008). */
@Composable
fun ThemePicker(themes: List<ThemeDefinition>, selectedId: String, dark: Boolean, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val fonts = remember(context) { ThemeFonts(context.assets) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        themes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        selected = theme.id == selectedId,
                        dark = dark,
                        fonts = fonts,
                        onClick = { onSelect(theme.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeDefinition,
    selected: Boolean,
    dark: Boolean,
    fonts: ThemeFonts,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = if (dark) theme.dark else theme.light
    val locale = LocalConfiguration.current.locales[0]
    val heading = theme.typography.heading
    val number = theme.typography.number
    val headingFamily = remember(heading) { fonts.family(heading) }
    val numberFamily = remember(number) { fonts.family(number) }
    val description = if (selected) {
        stringResource(R.string.settings_theme_selected, theme.name)
    } else {
        theme.name
    }
    Surface(
        selected = selected,
        onClick = onClick,
        shape = GoalMakerShapes.corner(minOf(theme.shapes.card, 20)),
        color = Color(palette.background),
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.semantics {
            role = Role.RadioButton
            contentDescription = description
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(14.dp)
                .clearAndSetSemantics { },
        ) {
            Text(
                text = if (heading.uppercase) theme.name.uppercase(locale) else theme.name,
                color = Color(palette.text),
                fontFamily = headingFamily,
                fontWeight = FontWeight(heading.weight),
                fontStyle = style(heading),
                fontSize = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .background(Color(palette.hero), GoalMakerShapes.corner(minOf(theme.shapes.card, 12)))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "3/5",
                        color = Color(palette.heroAccent),
                        fontFamily = numberFamily,
                        fontWeight = FontWeight(number.weight),
                        fontStyle = style(number),
                        fontSize = 22.sp,
                    )
                }
                Box(Modifier.size(20.dp).background(Color(palette.accent), GoalMakerShapes.corner(theme.shapes.checkbox)))
                Box(Modifier.size(20.dp).background(Color(palette.primary), CircleShape))
            }
        }
    }
}

private fun style(type: TypeStyle) = if (type.italic) FontStyle.Italic else FontStyle.Normal
