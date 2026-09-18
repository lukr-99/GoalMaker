package com.goalmaker.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.goalmaker.app.domain.design.ThemeShapes

/** The theme's corners for cards, list rows, checkboxes and buttons. */
@Immutable
data class GoalMakerShapes(
    val card: Shape,
    val row: Shape,
    val checkbox: Shape,
    val button: Shape,
) {
    companion object {
        fun from(shapes: ThemeShapes) = GoalMakerShapes(
            card = corner(shapes.card),
            row = corner(shapes.row),
            checkbox = corner(shapes.checkbox),
            button = corner(shapes.button),
        )

        /** A radius in dp; ThemeShapes.FULLY_ROUND and above means a pill or circle. */
        fun corner(radius: Int): CornerBasedShape =
            if (radius >= ThemeShapes.FULLY_ROUND) RoundedCornerShape(50) else RoundedCornerShape(radius.dp)
    }
}
