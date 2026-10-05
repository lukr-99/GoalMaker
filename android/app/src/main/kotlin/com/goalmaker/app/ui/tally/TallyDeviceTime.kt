package com.goalmaker.app.ui.tally

/** One device's week in the Tally place: [kind] is phone or pc, and [bar] its minutes by category. */
data class TallyDeviceTime(val kind: String, val bar: TallyBar)
