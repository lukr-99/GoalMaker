package com.goalmaker.app.application.planning

import java.time.LocalDate

/** The wants notification to show: the planning day it belongs to and the wants it names, in order. */
data class WantsDue(val day: LocalDate, val wantIds: List<String>)
