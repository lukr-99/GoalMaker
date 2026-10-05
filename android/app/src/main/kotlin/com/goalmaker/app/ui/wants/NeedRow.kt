package com.goalmaker.app.ui.wants

import com.goalmaker.app.application.planning.WantItem

/** A need as the Needs tab draws it, and whether its day has passed ([late]). */
data class NeedRow(val want: WantItem, val late: Boolean)
