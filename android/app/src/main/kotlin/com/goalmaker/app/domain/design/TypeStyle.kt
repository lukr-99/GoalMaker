package com.goalmaker.app.domain.design

/** A heading or number style: which font, how heavy, how wide, and whether it slants or shouts. */
data class TypeStyle(
    val family: String,
    val weight: Int,
    val width: Float,
    val italic: Boolean,
    val uppercase: Boolean,
    val tracking: Float,
)
