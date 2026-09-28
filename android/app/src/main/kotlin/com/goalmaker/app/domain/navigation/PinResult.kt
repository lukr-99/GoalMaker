package com.goalmaker.app.domain.navigation

/** The pins after pinning or unpinning, and whether the change was refused (the pins are unchanged then). */
data class PinResult(val pins: List<String>, val refused: Boolean)
