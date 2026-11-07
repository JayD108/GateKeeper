package com.gatekeeper.alert.util

import java.util.Calendar

/**
 * Utility to check whether the current time falls within the commute window.
 * Active window: 5:00 PM (17:00) to 8:00 PM (20:00).
 */
object TimeWindowFilter {

    private const val COMMUTE_START_HOUR = 17  // 5:00 PM
    private const val COMMUTE_END_HOUR = 20    // 8:00 PM

    /**
     * Returns true if current time is within the active commute window.
     */
    fun isWithinCommuteWindow(): Boolean {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour in COMMUTE_START_HOUR until COMMUTE_END_HOUR
    }

    /**
     * Returns the time remaining (in minutes) until the commute window opens.
     * Returns 0 if already within the window.
     * Returns -1 if the window has already passed today.
     */
    fun minutesUntilWindow(): Int {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        return when {
            hour < COMMUTE_START_HOUR -> {
                (COMMUTE_START_HOUR - hour) * 60 - minute
            }
            hour in COMMUTE_START_HOUR until COMMUTE_END_HOUR -> 0
            else -> -1 // Window passed
        }
    }
}

