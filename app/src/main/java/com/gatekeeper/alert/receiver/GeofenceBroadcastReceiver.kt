package com.gatekeeper.alert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gatekeeper.alert.service.TrackingService
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.gatekeeper.alert.util.TimeWindowFilter
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence transition broadcasts from the Android OS.
 *
 * Handles:
 *   - L1 EXIT → Check time window → Start tracking + notify P2 of departure
 *   - L2 Proximity ENTER → Trigger Stage 2 alarm on P2
 *   - L2 Arrival ENTER → Stop all tracking
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            when (geofence.requestId) {
                Constants.GEOFENCE_L1_OFFICE -> handleL1Transition(context, transitionType)
                Constants.GEOFENCE_L2_HOME_PROXIMITY -> handleL2ProximityTransition(context, transitionType)
                Constants.GEOFENCE_L2_HOME_ARRIVAL -> handleL2ArrivalTransition(context, transitionType)
            }
        }
    }

    /**
     * L1 (Office) EXIT: P1 has left the office.
     * Only triggers if within the commute time window (5 PM - 8 PM).
     */
    private fun handleL1Transition(context: Context, transitionType: Int) {
        if (transitionType != Geofence.GEOFENCE_TRANSITION_EXIT) return

        if (!TimeWindowFilter.isWithinCommuteWindow()) {
            Log.i(TAG, "L1 EXIT detected but outside commute window — suppressing")
            return
        }

        Log.i(TAG, "L1 EXIT within commute window — starting tracking service")

        // Start the foreground tracking service
        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
        }
        context.startForegroundService(serviceIntent)
    }

    /**
     * L2 Proximity (500m) ENTER: P1 is ~3 minutes from home.
     * Sends Stage 2 alarm trigger to P2 via Firestore (or SMS fallback).
     */
    private fun handleL2ProximityTransition(context: Context, transitionType: Int) {
        if (transitionType != Geofence.GEOFENCE_TRANSITION_ENTER) return

        Log.i(TAG, "L2 PROXIMITY ENTER — triggering Stage 2 alarm on P2")

        // Signal the tracking service to send proximity alarm
        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_TRIGGER_PROXIMITY_ALARM
        }
        context.startService(serviceIntent)
    }

    /**
     * L2 Arrival (50m) ENTER: P1 has arrived at home.
     * Stops all tracking and background services.
     */
    private fun handleL2ArrivalTransition(context: Context, transitionType: Int) {
        if (transitionType != Geofence.GEOFENCE_TRANSITION_ENTER) return

        Log.i(TAG, "L2 ARRIVAL ENTER — stopping all tracking")

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_TRACKING
        }
        context.startService(serviceIntent)
    }
}

