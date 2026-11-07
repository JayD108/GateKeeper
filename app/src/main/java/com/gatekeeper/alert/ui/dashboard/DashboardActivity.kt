package com.gatekeeper.alert.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.gatekeeper.alert.R
import com.gatekeeper.alert.databinding.ActivityDashboardBinding
import com.gatekeeper.alert.geofence.GeofenceManager
import com.gatekeeper.alert.service.TrackingService
import com.gatekeeper.alert.ui.alarm.AlarmActivity
import com.gatekeeper.alert.ui.map.LocationSetupActivity
import com.gatekeeper.alert.ui.setup.PairingActivity
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Main dashboard screen for GateKeeper.
 *
 * Shows:
 *   - Current status (idle / tracking / paired)
 *   - Setup buttons (set locations, pair devices)
 *   - Role selection (P1 or P2) on first launch
 *   - "HONK GATE" manual override button (P1 only)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var geofenceManager: GeofenceManager
    private val firestore = FirebaseFirestore.getInstance()

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted ✅", Toast.LENGTH_SHORT).show()
            setupGeofencesIfReady()
        } else {
            Toast.makeText(this, getString(R.string.error_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    // Background location permission (requested separately per Android policy)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Background location granted ✅", Toast.LENGTH_SHORT).show()
            setupGeofencesIfReady()
        } else {
            Toast.makeText(this, "Background location is required for geofencing", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geofenceManager = GeofenceManager(this)

        setupUI()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusDisplay()
    }

    private fun setupUI() {
        val role = PrefsManager.getRole(this)

        if (role == null) {
            // First launch — show role selection
            showRoleSelection()
        } else {
            hideRoleSelection()
        }

        // Set Locations button
        binding.btnSetLocations.setOnClickListener {
            startActivity(Intent(this, LocationSetupActivity::class.java))
        }

        // Pair Devices button
        binding.btnPairDevices.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }

        // HONK GATE manual override
        binding.btnHonkGate.setOnClickListener {
            honkGate()
        }
    }

    /**
     * Show role selection buttons (first launch only).
     */
    private fun showRoleSelection() {
        binding.tvRoleLabel.visibility = View.VISIBLE
        binding.btnRoleP1.visibility = View.VISIBLE
        binding.btnRoleP2.visibility = View.VISIBLE

        binding.btnRoleP1.setOnClickListener {
            PrefsManager.setRole(this, "P1")
            hideRoleSelection()
            updateStatusDisplay()
            requestPermissions()
            Toast.makeText(this, "You are P1 (Commuter) 🚗", Toast.LENGTH_SHORT).show()
        }

        binding.btnRoleP2.setOnClickListener {
            PrefsManager.setRole(this, "P2")
            hideRoleSelection()
            updateStatusDisplay()
            Toast.makeText(this, "You are P2 (Home) 🏠", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideRoleSelection() {
        binding.tvRoleLabel.visibility = View.GONE
        binding.btnRoleP1.visibility = View.GONE
        binding.btnRoleP2.visibility = View.GONE
    }

    /**
     * Update the status card with current state.
     */
    private fun updateStatusDisplay() {
        val role = PrefsManager.getRole(this)

        // Pairing status
        binding.tvPairingStatus.text = if (PrefsManager.isPaired(this)) {
            getString(R.string.status_paired)
        } else {
            getString(R.string.status_not_paired)
        }

        // Location status
        val l1Set = PrefsManager.isL1Set(this)
        val l2Set = PrefsManager.isL2Set(this)
        binding.tvLocationStatus.text = when {
            l1Set && l2Set -> "Locations: Office ✅ Home ✅"
            l1Set -> "Locations: Office ✅ Home ❌"
            l2Set -> "Locations: Office ❌ Home ✅"
            else -> "Locations: Not set"
        }

        // HONK GATE visibility — only for P1
        binding.btnHonkGate.visibility = if (role == "P1") View.VISIBLE else View.GONE

        // Status text
        binding.tvStatus.text = when {
            role == null -> "Please select your role"
            !PrefsManager.areLocationsSet(this) -> "Please set Office and Home locations"
            !PrefsManager.isPaired(this) -> "Please pair with your partner device"
            else -> getString(R.string.status_idle)
        }
    }

    /**
     * Manual override — send alarm to P2 immediately.
     */
    private fun honkGate() {
        Toast.makeText(this, "🔔 HONKING GATE!", Toast.LENGTH_SHORT).show()

        val honkData = hashMapOf(
            "type" to "honk",
            "timestamp" to FieldValue.serverTimestamp(),
            "sessionId" to PrefsManager.getSessionId(this)
        )

        firestore.collection("alerts")
            .add(honkData)
            .addOnSuccessListener {
                Toast.makeText(this, "Alarm sent to P2! 📢", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Fallback to SMS
                Toast.makeText(this, "Internet failed — sending SMS fallback", Toast.LENGTH_SHORT).show()
                sendHonkSms()
            }
    }

    private fun sendHonkSms() {
        val phone = PrefsManager.getPartnerPhone(this)
        if (phone.isNullOrEmpty()) {
            Toast.makeText(this, "No partner phone number set", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, Constants.SMS_CMD_ALARM, null, null)
            Toast.makeText(this, "SMS alarm sent! 📱", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Request all necessary runtime permissions.
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // SMS permissions (side-loaded only)
        permissions.add(Manifest.permission.SEND_SMS)
        permissions.add(Manifest.permission.RECEIVE_SMS)

        val needsPermission = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission.isNotEmpty()) {
            permissionLauncher.launch(needsPermission.toTypedArray())
        } else {
            // All foreground permissions granted — now request background location
            requestBackgroundLocationIfNeeded()
        }
    }

    /**
     * Background location must be requested separately (Android 11+).
     */
    private fun requestBackgroundLocationIfNeeded() {
        if (PrefsManager.isP1(this) &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            setupGeofencesIfReady()
        }
    }

    /**
     * Register geofences if all prerequisites are met.
     */
    private fun setupGeofencesIfReady() {
        if (PrefsManager.isP1(this) && PrefsManager.areLocationsSet(this)) {
            geofenceManager.registerGeofences()
        }
    }
}

