package com.goalmaker.app.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.goalmaker.app.domain.notes.LightMarkdown
import com.goalmaker.app.domain.notes.MarkdownBlock
import com.goalmaker.app.ui.theme.AppTheme

/** A task's notes in light Markdown (docs/archive.md): lines, list items, bold, italic and links you can open. */
@Composable
fun MarkdownNotes(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { LightMarkdown.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        blocks.forEach { block ->
            if (block.bullet) {
                Row {
                    Text("\u2022", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                    Line(block)
                }
            } else {
                Line(block)
            }
        }
    }
}

@Composable
private fun Line(block: MarkdownBlock) {
    val linkColor = AppTheme.colors.accent
    val line = buildAnnotatedString {
        block.spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
            )
            val link = span.link
            if (link != null) {
                withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(span.text)
                }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
    Text(line, style = MaterialTheme.typography.bodyLarge)
}
