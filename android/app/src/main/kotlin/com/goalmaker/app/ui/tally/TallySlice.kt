package com.goalmaker.app.ui.tally

/**
 * One part of a stacked Tally bar: a category's minutes, with the name it shows (blank for a category
 * the owner has since removed) and its area palette color.
 */
data class TallySlice(val category: String, val name: String, val color: String, val minutes: Int)
