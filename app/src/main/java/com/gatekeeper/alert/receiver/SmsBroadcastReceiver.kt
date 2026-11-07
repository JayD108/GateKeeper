package com.gatekeeper.alert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.gatekeeper.alert.ui.alarm.AlarmActivity
import com.gatekeeper.alert.util.Constants

/**
 * Intercepts incoming SMS messages looking for GateKeeper commands.
 * This is the offline fallback — when P1 has no internet, they send
 * a structured SMS that P2's app intercepts to trigger the alarm locally.
 *
 * Commands:
 *   GATEKEEPER_CMD:ALARM     → Trigger Stage 2 alarm
 *   GATEKEEPER_CMD:DEPARTURE → Show departure notification
 *   GATEKEEPER_CMD:ACK       → Show acknowledgment
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val body = sms.messageBody?.trim() ?: continue
            Log.d(TAG, "SMS received: $body")

            when {
                body.startsWith(Constants.SMS_CMD_ALARM) -> {
                    Log.i(TAG, "ALARM command received via SMS — launching alarm")
                    launchAlarm(context, Constants.ALARM_TYPE_PROXIMITY)
                    abortBroadcast() // Prevent SMS from showing in inbox
                }
                body.startsWith(Constants.SMS_CMD_DEPARTURE) -> {
                    Log.i(TAG, "DEPARTURE command received via SMS")
                    // TODO: Show departure notification locally
                    abortBroadcast()
                }
                body.startsWith(Constants.SMS_CMD_ACK) -> {
                    Log.i(TAG, "ACK command received via SMS")
                    // TODO: Show acknowledgment on P1's screen
                    abortBroadcast()
                }
            }
        }
    }

    private fun launchAlarm(context: Context, alarmType: String) {
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_ALARM_TYPE, alarmType)
        }
        context.startActivity(alarmIntent)
    }
}

