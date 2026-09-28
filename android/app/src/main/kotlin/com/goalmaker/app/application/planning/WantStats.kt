package com.goalmaker.app.application.planning

/** The stats block for wants: how many were bought and dropped, and the dropped prices added up. */
data class WantStats(val bought: Int, val dropped: Int, val notSpent: Double)
