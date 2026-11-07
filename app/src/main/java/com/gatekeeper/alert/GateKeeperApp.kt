package com.gatekeeper.alert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class for GateKeeper.
 * Sets up notification channels on app start.
 */
class GateKeeperApp : Application() {

    companion object {
        const val CHANNEL_DEPARTURE = "gatekeeper_departure"
        const val CHANNEL_ALARM = "gatekeeper_alarm"
        const val CHANNEL_TRACKING = "gatekeeper_tracking"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // Departure notifications (Stage 1 — normal priority)
        val departureChannel = NotificationChannel(
            CHANNEL_DEPARTURE,
            getString(R.string.channel_departure_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.channel_departure_desc)
        }

        // Alarm notifications (Stage 2 — max priority for full-screen intent)
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_alarm_desc)
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // Foreground tracking service channel
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING,
            getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_tracking_desc)
        }

        manager.createNotificationChannels(
            listOf(departureChannel, alarmChannel, trackingChannel)
        )
    }
}

