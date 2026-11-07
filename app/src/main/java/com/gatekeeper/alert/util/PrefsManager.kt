package com.gatekeeper.alert.util

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng

/**
 * SharedPreferences wrapper for persisting GateKeeper configuration.
 * Stores role, paired session, L1/L2 coordinates, and partner phone number.
 */
object PrefsManager {

    private const val PREFS_NAME = "gatekeeper_prefs"

    // Keys
    private const val KEY_ROLE = "role"                     // "P1" or "P2"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_PAIRED = "is_paired"
    private const val KEY_PARTNER_FCM_TOKEN = "partner_fcm_token"
    private const val KEY_PARTNER_PHONE = "partner_phone"
    private const val KEY_L1_LAT = "l1_lat"
    private const val KEY_L1_LNG = "l1_lng"
    private const val KEY_L2_LAT = "l2_lat"
    private const val KEY_L2_LNG = "l2_lng"
    private const val KEY_L1_SET = "l1_set"
    private const val KEY_L2_SET = "l2_set"
    private const val KEY_FCM_TOKEN = "my_fcm_token"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Role ---
    fun setRole(ctx: Context, role: String) {
        prefs(ctx).edit().putString(KEY_ROLE, role).apply()
    }

    fun getRole(ctx: Context): String? = prefs(ctx).getString(KEY_ROLE, null)

    fun isP1(ctx: Context): Boolean = getRole(ctx) == "P1"
    fun isP2(ctx: Context): Boolean = getRole(ctx) == "P2"

    // --- Pairing ---
    fun setSessionId(ctx: Context, sessionId: String) {
        prefs(ctx).edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    fun getSessionId(ctx: Context): String? = prefs(ctx).getString(KEY_SESSION_ID, null)

    fun setPaired(ctx: Context, paired: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PAIRED, paired).apply()
    }

    fun isPaired(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PAIRED, false)

    fun setPartnerFcmToken(ctx: Context, token: String) {
        prefs(ctx).edit().putString(KEY_PARTNER_FCM_TOKEN, token).apply()
    }

    fun getPartnerFcmToken(ctx: Context): String? =
        prefs(ctx).getString(KEY_PARTNER_FCM_TOKEN, null)

    fun setPartnerPhone(ctx: Context, phone: String) {
        prefs(ctx).edit().putString(KEY_PARTNER_PHONE, phone).apply()
    }

    fun getPartnerPhone(ctx: Context): String? = prefs(ctx).getString(KEY_PARTNER_PHONE, null)

    // --- FCM Token ---
    fun setMyFcmToken(ctx: Context, token: String) {
        prefs(ctx).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getMyFcmToken(ctx: Context): String? = prefs(ctx).getString(KEY_FCM_TOKEN, null)

    // --- L1 (Office) ---
    fun setL1(ctx: Context, lat: Double, lng: Double) {
        prefs(ctx).edit()
            .putLong(KEY_L1_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_L1_LNG, java.lang.Double.doubleToRawLongBits(lng))
            .putBoolean(KEY_L1_SET, true)
            .apply()
    }

    fun getL1(ctx: Context): LatLng? {
        if (!prefs(ctx).getBoolean(KEY_L1_SET, false)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs(ctx).getLong(KEY_L1_LAT, 0))
        val lng = java.lang.Double.longBitsToDouble(prefs(ctx).getLong(KEY_L1_LNG, 0))
        return LatLng(lat, lng)
    }

    fun isL1Set(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_L1_SET, false)

    // --- L2 (Home) ---
    fun setL2(ctx: Context, lat: Double, lng: Double) {
        prefs(ctx).edit()
            .putLong(KEY_L2_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_L2_LNG, java.lang.Double.doubleToRawLongBits(lng))
            .putBoolean(KEY_L2_SET, true)
            .apply()
    }

    fun getL2(ctx: Context): LatLng? {
        if (!prefs(ctx).getBoolean(KEY_L2_SET, false)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs(ctx).getLong(KEY_L2_LAT, 0))
        val lng = java.lang.Double.longBitsToDouble(prefs(ctx).getLong(KEY_L2_LNG, 0))
        return LatLng(lat, lng)
    }

    fun isL2Set(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_L2_SET, false)

    fun areLocationsSet(ctx: Context): Boolean = isL1Set(ctx) && isL2Set(ctx)
}

