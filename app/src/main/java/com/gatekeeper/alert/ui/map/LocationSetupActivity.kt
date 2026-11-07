package com.gatekeeper.alert.ui.map

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gatekeeper.alert.R
import com.gatekeeper.alert.databinding.ActivityLocationSetupBinding
import com.gatekeeper.alert.geofence.GeofenceManager
import com.gatekeeper.alert.util.PrefsManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Map-based location setup screen.
 *
 * Flow:
 *   1. User taps the map to select the L1 (Office) location → Confirm
 *   2. Map resets, user taps to select L2 (Home) location → Confirm
 *   3. Both locations saved to SharedPreferences
 *   4. Geofences registered (if P1)
 *   5. Activity finishes
 */
class LocationSetupActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityLocationSetupBinding
    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null

    // State machine: are we setting L1 or L2?
    private enum class SetupStep { L1_OFFICE, L2_HOME }
    private var currentStep = SetupStep.L1_OFFICE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Confirm button
        binding.btnConfirmLocation.setOnClickListener {
            confirmLocation()
        }

        updateInstruction()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map for simplicity (elder-friendly)
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = false
        }

        // Default camera: India center (will be updated when user taps)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 5f))

        // Try to show current location
        try {
            map.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            // Location permission not granted — ignore
        }

        // Map tap listener
        map.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            map.clear()
            drawSelection(latLng)
            binding.btnConfirmLocation.isEnabled = true
            binding.tvCoords.text = String.format(
                "📍 Lat: %.6f, Lng: %.6f", latLng.latitude, latLng.longitude
            )
        }
    }

    /**
     * Draw a marker and radius circle at the selected location.
     */
    private fun drawSelection(latLng: LatLng) {
        val map = googleMap ?: return

        // Draw previously saved location if exists
        when (currentStep) {
            SetupStep.L2_HOME -> {
                // Show L1 that was already saved
                PrefsManager.getL1(this)?.let { l1 ->
                    map.addMarker(
                        MarkerOptions()
                            .position(l1)
                            .title("Office (L1)")
                    )
                    map.addCircle(
                        CircleOptions()
                            .center(l1)
                            .radius(200.0)
                            .strokeColor(0x551565C0.toInt())
                            .fillColor(0x221565C0.toInt())
                            .strokeWidth(2f)
                    )
                }
            }
            else -> { /* No previous location to show */ }
        }

        // Draw selected location
        val title = when (currentStep) {
            SetupStep.L1_OFFICE -> "Office (L1)"
            SetupStep.L2_HOME -> "Home (L2)"
        }

        val radius = when (currentStep) {
            SetupStep.L1_OFFICE -> 200.0
            SetupStep.L2_HOME -> 500.0
        }

        val color = when (currentStep) {
            SetupStep.L1_OFFICE -> 0x551565C0.toInt()
            SetupStep.L2_HOME -> 0x55FF6F00.toInt()
        }

        val fillColor = when (currentStep) {
            SetupStep.L1_OFFICE -> 0x221565C0.toInt()
            SetupStep.L2_HOME -> 0x22FF6F00.toInt()
        }

        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
        )

        map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radius)
                .strokeColor(color)
                .fillColor(fillColor)
                .strokeWidth(3f)
        )

        // Zoom to the selected point
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    /**
     * Confirm the currently selected location and advance to the next step.
     */
    private fun confirmLocation() {
        val latLng = selectedLatLng ?: return

        when (currentStep) {
            SetupStep.L1_OFFICE -> {
                PrefsManager.setL1(this, latLng.latitude, latLng.longitude)
                Toast.makeText(this, "Office location saved! ✅", Toast.LENGTH_SHORT).show()

                // Advance to L2
                currentStep = SetupStep.L2_HOME
                selectedLatLng = null
                binding.btnConfirmLocation.isEnabled = false
                googleMap?.clear()
                updateInstruction()
            }

            SetupStep.L2_HOME -> {
                PrefsManager.setL2(this, latLng.latitude, latLng.longitude)
                Toast.makeText(this, "Home location saved! ✅", Toast.LENGTH_SHORT).show()

                // Register geofences if P1
                if (PrefsManager.isP1(this)) {
                    GeofenceManager(this).registerGeofences()
                }

                // All done
                Toast.makeText(this, "Both locations saved! 🎉", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * Update the instruction banner text.
     */
    private fun updateInstruction() {
        binding.tvInstruction.text = when (currentStep) {
            SetupStep.L1_OFFICE -> getString(R.string.title_set_office)
            SetupStep.L2_HOME -> getString(R.string.title_set_home)
        }
    }
}

