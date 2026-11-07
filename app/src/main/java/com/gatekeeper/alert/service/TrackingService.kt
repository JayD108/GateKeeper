package com.gatekeeper.alert.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.gatekeeper.alert.GateKeeperApp
import com.gatekeeper.alert.R
import com.gatekeeper.alert.ui.dashboard.DashboardActivity
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.android.gms.location.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Foreground Service that activates when P1 exits the L1 (Office) geofence.
 *
 * Responsibilities:
 *   1. Start a persistent foreground notification ("Tracking Active").
 *   2. Request high-accuracy GPS updates every 10 seconds.
 *   3. Write each location fix to Firestore: sessions/{sessionId}/locations
 *   4. On ACTION_TRIGGER_PROXIMITY_ALARM: write proximity event to Firestore
 *      (or send SMS fallback if offline).
 *   5. On ACTION_STOP_TRACKING: clean up everything.
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "TrackingService"

        const val ACTION_START_TRACKING = "com.gatekeeper.alert.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.gatekeeper.alert.STOP_TRACKING"
        const val ACTION_TRIGGER_PROXIMITY_ALARM = "com.gatekeeper.alert.TRIGGER_PROXIMITY_ALARM"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private var locationCallback: LocationCallback? = null
    private var currentSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()
        Log.d(TAG, "TrackingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_TRIGGER_PROXIMITY_ALARM -> triggerProximityAlarm()
            else -> Log.w(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    /**
     * Begin live GPS tracking with a foreground notification.
     */
    private fun startTracking() {
        Log.i(TAG, "Starting live GPS tracking")

        // Start foreground notification immediately
        startForeground(Constants.NOTIF_ID_TRACKING, buildTrackingNotification())

        // Create a new session in Firestore
        createSession()

        // Send departure notification to P2
        sendDepartureNotification()

        // Start location updates
        startLocationUpdates()
    }

    /**
     * Stop all tracking, remove notification, and stop the service.
     */
    private fun stopTracking() {
        Log.i(TAG, "Stopping live GPS tracking")

        // Stop location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null

        // Mark session as completed in Firestore
        currentSessionId?.let { sessionId ->
            firestore.collection(Constants.COLLECTION_SESSIONS)
                .document(sessionId)
                .update(
                    mapOf(
                        "status" to "completed",
                        "endTime" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener { Log.i(TAG, "Session marked complete") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to close session", e) }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Create a new tracking session document in Firestore.
     */
    private fun createSession() {
        val sessionData = hashMapOf(
            "status" to "active",
            "startTime" to FieldValue.serverTimestamp(),
            "p1_battery" to getBatteryLevel(),
            "p2_acknowledged" to false
        )

        firestore.collection(Constants.COLLECTION_SESSIONS)
            .add(sessionData)
            .addOnSuccessListener { docRef ->
                currentSessionId = docRef.id
                Log.i(TAG, "Session created: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create session", e)
            }
    }

    /**
     * Send Stage 1 departure notification to P2.
     * First tries Firestore (which triggers a Cloud Function → FCM).
     * Falls back to SMS if Firestore write fails.
     */
    private fun sendDepartureNotification() {
        val batteryLevel = getBatteryLevel()
        val sessionId = PrefsManager.getSessionId(this)

        val departureData = hashMapOf(
            "type" to "departure",
            "timestamp" to FieldValue.serverTimestamp(),
            "p1_battery" to batteryLevel,
            "sessionId" to sessionId
        )

        firestore.collection("alerts")
            .add(departureData)
            .addOnSuccessListener {
                Log.i(TAG, "Departure alert sent to Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore write failed, falling back to SMS", e)
                sendSmsFallback(Constants.SMS_CMD_DEPARTURE)
            }
    }

    /**
     * Request high-accuracy location updates every 10 seconds.
     * Each fix is written to Firestore: sessions/{sessionId}/locations
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing location permission — cannot start updates")
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.TRACKING_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(Constants.TRACKING_INTERVAL_MS / 2)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")

                // Write to Firestore
                val sessionId = currentSessionId ?: return
                val locationData = hashMapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "accuracy" to location.accuracy
                )

                firestore.collection(Constants.COLLECTION_SESSIONS)
                    .document(sessionId)
                    .collection(Constants.COLLECTION_LOCATIONS)
                    .add(locationData)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to write location", e)
                    }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    /**
     * P1 has entered the 500m proximity zone around L2.
     * Trigger Stage 2 alarm on P2 via Firestore (or SMS fallback).
     */
    private fun triggerProximityAlarm() {
        Log.i(TAG, "Triggering Stage 2 proximity alarm on P2")

        val alarmData = hashMapOf(
            "type" to "proximity_alarm",
            "timestamp" to FieldValue.serverTimestamp(),
            "sessionId" to currentSessionId
        )

        firestore.collection("alerts")
            .add(alarmData)
            .addOnSuccessListener {
                Log.i(TAG, "Proximity alarm sent to Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore write failed, falling back to SMS", e)
                sendSmsFallback(Constants.SMS_CMD_ALARM)
            }
    }

    /**
     * Send an SMS command to P2 as a fallback when internet is unavailable.
     */
    private fun sendSmsFallback(command: String) {
        val partnerPhone = PrefsManager.getPartnerPhone(this)
        if (partnerPhone.isNullOrEmpty()) {
            Log.e(TAG, "No partner phone number configured — cannot send SMS")
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(partnerPhone, null, command, null, null)
            Log.i(TAG, "SMS fallback sent: $command to $partnerPhone")
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
        }
    }

    /**
     * Get current battery level as a percentage (0-100).
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * Build the persistent foreground notification.
     */
    private fun buildTrackingNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GateKeeperApp.CHANNEL_TRACKING)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        Log.d(TAG, "TrackingService destroyed")
    }
}

