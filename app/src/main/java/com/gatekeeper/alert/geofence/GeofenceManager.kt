package com.gatekeeper.alert.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.gatekeeper.alert.receiver.GeofenceBroadcastReceiver
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

/**
 * Manages all geofence registration and removal for GateKeeper.
 *
 * Geofences registered:
 *   - L1 (Office, 200m): EXIT transition → triggers Stage 1 departure
 *   - L2 Proximity (Home, 500m): ENTER transition → triggers Stage 2 alarm
 *   - L2 Arrival (Home, 50m): ENTER transition → auto-stop tracking
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
    }

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            Constants.RC_GEOFENCE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Register all geofences based on the user's role.
     * P1 gets: L1 EXIT, L2 Proximity ENTER, L2 Arrival ENTER
     * P2 gets: none (P2 doesn't need geofences — alerts arrive via FCM/SMS)
     */
    fun registerGeofences() {
        if (!PrefsManager.isP1(context)) {
            Log.d(TAG, "Not P1, skipping geofence registration")
            return
        }

        val l1 = PrefsManager.getL1(context)
        val l2 = PrefsManager.getL2(context)

        if (l1 == null || l2 == null) {
            Log.w(TAG, "L1 or L2 not set, cannot register geofences")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing ACCESS_FINE_LOCATION permission")
            return
        }

        val geofences = buildGeofenceList(l1, l2)
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener {
                Log.i(TAG, "Geofences registered successfully (${geofences.size} fences)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register geofences", e)
            }
    }

    /**
     * Build the list of Geofence objects for P1.
     */
    private fun buildGeofenceList(l1: LatLng, l2: LatLng): List<Geofence> {
        return listOf(
            // L1 Office — trigger on EXIT (P1 leaving work)
            Geofence.Builder()
                .setRequestId(Constants.GEOFENCE_L1_OFFICE)
                .setCircularRegion(l1.latitude, l1.longitude, Constants.RADIUS_L1_OFFICE)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(30_000) // 30 seconds to avoid jitter
                .build(),

            // L2 Proximity — trigger on ENTER (Stage 2 alarm for P2)
            Geofence.Builder()
                .setRequestId(Constants.GEOFENCE_L2_HOME_PROXIMITY)
                .setCircularRegion(l2.latitude, l2.longitude, Constants.RADIUS_L2_PROXIMITY)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build(),

            // L2 Arrival — trigger on ENTER (auto-stop tracking)
            Geofence.Builder()
                .setRequestId(Constants.GEOFENCE_L2_HOME_ARRIVAL)
                .setCircularRegion(l2.latitude, l2.longitude, Constants.RADIUS_L2_ARRIVAL)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        )
    }

    /**
     * Remove all registered geofences.
     */
    fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.i(TAG, "All geofences removed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofences", e)
            }
    }
}

