package com.goalmaker.app.ui.widget

import kotlin.math.ceil

/**
 * How big the Motivation widget's words can be: the largest size, from [MAX] down to [MIN], at which
 * the lines fit the widget, so a short phrase fills it and a long list still shows. Glance can't
 * measure text, so this estimates: an average glyph is about [GLYPH] of the size wide, and a line is
 * [LEADING] of the size tall.
 */
object MotivationFit {
    const val MAX = 34f
    const val MIN = 12f
    private const val GLYPH = 0.56f
    private const val LEADING = 1.3f

    fun size(lines: List<String>, widthDp: Float, heightDp: Float): Float {
        if (lines.isEmpty() || widthDp <= 0f || heightDp <= 0f) return MIN
        var size = MAX
        while (size > MIN && !fits(lines, widthDp, heightDp, size)) size -= 1f
        return size
    }

    /** How many rows [lines] take at [size] in a box [widthDp] wide, each line wrapping on its own. */
    fun rows(lines: List<String>, widthDp: Float, size: Float): Int {
        val perRow = (widthDp / (size * GLYPH)).toInt().coerceAtLeast(1)
        return lines.sumOf { line -> ceil(line.length.coerceAtLeast(1) / perRow.toDouble()).toInt() }
    }

    private fun fits(lines: List<String>, widthDp: Float, heightDp: Float, size: Float): Boolean =
        rows(lines, widthDp, size) * size * LEADING <= heightDp
}
