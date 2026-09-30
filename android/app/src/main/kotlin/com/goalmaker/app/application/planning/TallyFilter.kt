package com.goalmaker.app.application.planning

/**
 * What the Tally place and the stats show: one [kind] of device (`phone` or `pc`) and one [category],
 * or everything where either is null (contracts/vectors/tally.json 'weeks').
 */
data class TallyFilter(val kind: String? = null, val category: String? = null)
