package com.goalmaker.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Asserts the node is laid out whole inside [outer]: not pushed past an edge or cut off by it, and
 * not squeezed to nothing. It compares unclipped bounds, so a node a parent cuts off still fails.
 * Large-text tests use it with the screen's root and with a row or a cell.
 */
fun SemanticsNodeInteraction.assertLaidOutIn(outer: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val box = getUnclippedBoundsInRoot()
    val bounds = outer.getUnclippedBoundsInRoot()
    val inside = box.left >= bounds.left - SLACK && box.top >= bounds.top - SLACK &&
        box.right <= bounds.right + SLACK && box.bottom <= bounds.bottom + SLACK
    assertTrue("$box is not inside $bounds", inside && box.right > box.left && box.bottom > box.top)
    return this
}

// Rounding between pixels and dp.
private val SLACK = 0.5.dp
