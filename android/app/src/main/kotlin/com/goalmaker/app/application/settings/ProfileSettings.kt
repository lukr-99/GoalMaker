package com.goalmaker.app.application.settings

/**
 * The server's copy of the settings the connector needs to know what "today" is (the profile's time
 * zone and day start, supabase/migrations/0001). The device's settings stay the source; this keeps the
 * profile in step with them.
 */
interface ProfileSettings {
    /** Writes the device's time zone id and planning-day start hour to the profile. */
    suspend fun update(timeZone: String, dayStartHour: Int)
}
