package com.gatekeeper.alert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gatekeeper.alert.geofence.GeofenceManager
import com.gatekeeper.alert.util.PrefsManager

/**
 * Re-registers geofences after device reboot.
 * Android clears all geofences on reboot, so we must re-register them.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed — re-registering geofences")

        if (PrefsManager.isP1(context) && PrefsManager.areLocationsSet(context)) {
            GeofenceManager(context).registerGeofences()
        }
    }
}

