package com.goalmaker.app.application.planning

/**
 * The owner's thresholds for a new want's cooldown (docs/wants.md): under [smallUnder] it waits
 * [smallDays], under [mediumUnder] [mediumDays], anything more [largeDays], and without a price or in
 * another currency than [currency] [unpricedDays]. One synced row per owner; [DEFAULT] without it.
 */
data class WantCooldowns(
    val smallUnder: Double,
    val smallDays: Int,
    val mediumUnder: Double,
    val mediumDays: Int,
    val largeDays: Int,
    val unpricedDays: Int,
    val currency: String,
) {
    companion object {
        /** What a new owner starts with, pinned by the 'defaults' of contracts/vectors/wants.json. */
        val DEFAULT = WantCooldowns(
            smallUnder = 1000.0,
            smallDays = 7,
            mediumUnder = 10000.0,
            mediumDays = 30,
            largeDays = 90,
            unpricedDays = 30,
            currency = "CZK",
        )
    }
}
