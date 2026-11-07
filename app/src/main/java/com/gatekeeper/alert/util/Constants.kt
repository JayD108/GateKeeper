package com.gatekeeper.alert.util

/**
 * Constants used across the GateKeeper application.
 */
object Constants {

    // Geofence identifiers
    const val GEOFENCE_L1_OFFICE = "geofence_l1_office"
    const val GEOFENCE_L2_HOME_PROXIMITY = "geofence_l2_proximity"  // 500m
    const val GEOFENCE_L2_HOME_ARRIVAL = "geofence_l2_arrival"      // 50m

    // Geofence radii in meters
    const val RADIUS_L1_OFFICE = 200f           // L1 departure trigger
    const val RADIUS_L2_PROXIMITY = 500f        // Stage 2 alarm trigger
    const val RADIUS_L2_ARRIVAL = 50f           // Auto-stop tracking

    // Tracking
    const val TRACKING_INTERVAL_MS = 10_000L    // 10 seconds

    // SMS command protocol
    const val SMS_PREFIX = "GATEKEEPER_CMD:"
    const val SMS_CMD_ALARM = "${SMS_PREFIX}ALARM"
    const val SMS_CMD_DEPARTURE = "${SMS_PREFIX}DEPARTURE"
    const val SMS_CMD_ACK = "${SMS_PREFIX}ACK"

    // Firestore collections
    const val COLLECTION_SESSIONS = "sessions"
    const val COLLECTION_LOCATIONS = "locations"
    const val COLLECTION_DEVICES = "devices"

    // Intent extras
    const val EXTRA_ALARM_TYPE = "alarm_type"
    const val ALARM_TYPE_PROXIMITY = "proximity"
    const val ALARM_TYPE_HONK = "honk"

    // Notification IDs
    const val NOTIF_ID_TRACKING = 1001
    const val NOTIF_ID_DEPARTURE = 1002
    const val NOTIF_ID_ALARM = 1003

    // Geofence pending intent request codes
    const val RC_GEOFENCE = 2001

    // QR code pairing
    const val QR_PREFIX = "GATEKEEPER_PAIR:"
}

