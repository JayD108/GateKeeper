package com.gatekeeper.alert.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.gatekeeper.alert.GateKeeperApp
import com.gatekeeper.alert.R
import com.gatekeeper.alert.ui.alarm.AlarmActivity
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Handles incoming Firebase Cloud Messaging (FCM) messages.
 *
 * Message types:
 *   - "departure": Stage 1 → simple notification chime
 *   - "proximity_alarm": Stage 2 → full-screen alarm intent
 *   - "honk": Manual override → full-screen alarm intent
 *   - "ack": P2 acknowledged → silent notification on P1
 */
class GateKeeperMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM token: $token")
        PrefsManager.setMyFcmToken(this, token)

        // TODO: Update token in Firestore devices collection
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val type = message.data["type"] ?: return

        when (type) {
            "departure" -> handleDeparture(message.data)
            "proximity_alarm", "honk" -> handleAlarm(message.data)
            "ack" -> handleAcknowledgment(message.data)
            else -> Log.w(TAG, "Unknown message type: $type")
        }
    }

    /**
     * Stage 1: P1 has left the office → show a standard notification on P2.
     */
    private fun handleDeparture(data: Map<String, String>) {
        val battery = data["p1_battery"] ?: "?"
        val text = "P1 has left the office. (P1 Battery: $battery%)"

        val notification = NotificationCompat.Builder(this, GateKeeperApp.CHANNEL_DEPARTURE)
            .setContentTitle(getString(R.string.notif_departure_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(Constants.NOTIF_ID_DEPARTURE, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
    }

    /**
     * Stage 2 / Honk: P1 is near home or manually honking → launch full-screen alarm.
     */
    private fun handleAlarm(data: Map<String, String>) {
        val alarmType = data["type"] ?: Constants.ALARM_TYPE_PROXIMITY

        // Create the full-screen intent for AlarmActivity
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_ALARM_TYPE, alarmType)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, GateKeeperApp.CHANNEL_ALARM)
            .setContentTitle(getString(R.string.alarm_title))
            .setContentText(getString(R.string.alarm_subtitle))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(Constants.NOTIF_ID_ALARM, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }

        // Also launch the activity directly for reliability
        startActivity(alarmIntent)
    }

    /**
     * P2 acknowledged → show a confirmation on P1's screen.
     */
    private fun handleAcknowledgment(data: Map<String, String>) {
        val notification = NotificationCompat.Builder(this, GateKeeperApp.CHANNEL_DEPARTURE)
            .setContentTitle(getString(R.string.alarm_acknowledged))
            .setContentText("P2 has acknowledged and will unlock the gate.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(Constants.NOTIF_ID_DEPARTURE, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
    }
}

