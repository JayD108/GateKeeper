package com.gatekeeper.alert.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.gatekeeper.alert.R
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Home screen widget for the "HONK GATE" manual override.
 * A single 1x1 widget that, when tapped, immediately sends
 * a high-priority alarm to P2's phone.
 */
class HonkGateWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "HonkGateWidget"
        const val ACTION_HONK = "com.gatekeeper.alert.ACTION_HONK_GATE"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, HonkGateWidget::class.java).apply {
                action = ACTION_HONK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_honk_gate)
            views.setOnClickPendingIntent(R.id.tvHonk, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_HONK) {
            Log.i(TAG, "HONK GATE widget tapped!")
            triggerHonk(context)
        }
    }

    private fun triggerHonk(context: Context) {
        val honkData = hashMapOf(
            "type" to "honk",
            "timestamp" to FieldValue.serverTimestamp(),
            "sessionId" to PrefsManager.getSessionId(context)
        )

        FirebaseFirestore.getInstance()
            .collection("alerts")
            .add(honkData)
            .addOnSuccessListener {
                Log.i(TAG, "Honk alert sent via Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore failed, trying SMS fallback", e)
                sendHonkSms(context)
            }
    }

    private fun sendHonkSms(context: Context) {
        val phone = PrefsManager.getPartnerPhone(context)
        if (phone.isNullOrEmpty()) return
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, Constants.SMS_CMD_ALARM, null, null)
            Log.i(TAG, "Honk SMS sent")
        } catch (e: Exception) {
            Log.e(TAG, "Honk SMS failed", e)
        }
    }
}

