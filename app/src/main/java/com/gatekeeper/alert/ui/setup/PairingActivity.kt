package com.gatekeeper.alert.ui.setup

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gatekeeper.alert.R
import com.gatekeeper.alert.databinding.ActivityPairingBinding
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.util.UUID

/**
 * QR Code pairing screen.
 *
 * P1 Flow:
 *   1. Generates a unique session ID
 *   2. Stores it in Firestore under "devices" collection
 *   3. Encodes it as a QR code: "GATEKEEPER_PAIR:<sessionId>:<fcmToken>"
 *   4. Displays the QR code for P2 to scan
 *
 * P2 Flow:
 *   1. Scans P1's QR code
 *   2. Extracts the session ID and P1's FCM token
 *   3. Writes P2's FCM token to Firestore under the same session
 *   4. Both devices are now permanently paired
 */
class PairingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PairingActivity"
    }

    private lateinit var binding: ActivityPairingBinding
    private val firestore = FirebaseFirestore.getInstance()

    // ZXing barcode scanner launcher
    private val scanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            handleScannedQR(result.contents)
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val role = PrefsManager.getRole(this)

        when (role) {
            "P1" -> setupP1View()
            "P2" -> setupP2View()
            else -> {
                Toast.makeText(this, "Please select your role first", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * P1: Generate and display QR code.
     */
    private fun setupP1View() {
        binding.tvPairingTitle.text = getString(R.string.title_show_qr)
        binding.btnScanQr.visibility = View.GONE

        // Get FCM token then generate QR
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to get FCM token", task.exception)
                Toast.makeText(this, "Failed to get device token", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }

            val fcmToken = task.result
            PrefsManager.setMyFcmToken(this, fcmToken)

            val sessionId = UUID.randomUUID().toString().take(8).uppercase()
            PrefsManager.setSessionId(this, sessionId)

            // QR payload: GATEKEEPER_PAIR:<sessionId>:<fcmToken>
            val qrPayload = "${Constants.QR_PREFIX}$sessionId:$fcmToken"

            // Generate QR bitmap
            val bitmap = generateQRCode(qrPayload, 600)
            binding.ivQrCode.setImageBitmap(bitmap)
            binding.tvSessionId.text = "Session ID: $sessionId"

            // Store in Firestore
            val deviceData = hashMapOf(
                "sessionId" to sessionId,
                "p1_fcmToken" to fcmToken,
                "p2_fcmToken" to "",
                "paired" to false,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            firestore.collection(Constants.COLLECTION_DEVICES)
                .document(sessionId)
                .set(deviceData)
                .addOnSuccessListener {
                    Log.i(TAG, "Device session created: $sessionId")
                    binding.tvPairingStatus.apply {
                        text = "Waiting for P2 to scan…"
                        visibility = View.VISIBLE
                    }

                    // Listen for P2 pairing
                    listenForPairing(sessionId)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create device session", e)
                    Toast.makeText(this, "Failed to create session", Toast.LENGTH_LONG).show()
                }
        }
    }

    /**
     * P2: Show scan button to scan P1's QR code.
     */
    private fun setupP2View() {
        binding.tvPairingTitle.text = getString(R.string.title_scan_qr)
        binding.ivQrCode.visibility = View.GONE

        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Point camera at P1's QR code")
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(true)
            }
            scanLauncher.launch(options)
        }
    }

    /**
     * Handle the scanned QR code data (P2 side).
     */
    private fun handleScannedQR(contents: String) {
        Log.d(TAG, "Scanned QR: $contents")

        if (!contents.startsWith(Constants.QR_PREFIX)) {
            Toast.makeText(this, getString(R.string.pairing_failed), Toast.LENGTH_LONG).show()
            return
        }

        val data = contents.removePrefix(Constants.QR_PREFIX)
        val parts = data.split(":")
        if (parts.size < 2) {
            Toast.makeText(this, getString(R.string.pairing_failed), Toast.LENGTH_LONG).show()
            return
        }

        val sessionId = parts[0]
        val p1FcmToken = parts[1]

        PrefsManager.setSessionId(this, sessionId)
        PrefsManager.setPartnerFcmToken(this, p1FcmToken)

        // Get P2's FCM token and update Firestore
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to get P2 FCM token", task.exception)
                return@addOnCompleteListener
            }

            val p2Token = task.result
            PrefsManager.setMyFcmToken(this, p2Token)

            firestore.collection(Constants.COLLECTION_DEVICES)
                .document(sessionId)
                .update(
                    mapOf(
                        "p2_fcmToken" to p2Token,
                        "paired" to true
                    )
                )
                .addOnSuccessListener {
                    PrefsManager.setPaired(this, true)
                    Log.i(TAG, "Pairing successful! Session: $sessionId")

                    binding.tvPairingStatus.apply {
                        text = getString(R.string.pairing_success)
                        visibility = View.VISIBLE
                    }

                    Toast.makeText(this, getString(R.string.pairing_success), Toast.LENGTH_LONG).show()

                    // Finish after a brief delay
                    binding.root.postDelayed({ finish() }, 2000)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update pairing", e)
                    Toast.makeText(this, getString(R.string.pairing_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    /**
     * P1 listens for P2 to complete pairing in Firestore.
     */
    private fun listenForPairing(sessionId: String) {
        firestore.collection(Constants.COLLECTION_DEVICES)
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Pairing listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val paired = snapshot.getBoolean("paired") ?: false
                    if (paired) {
                        val p2Token = snapshot.getString("p2_fcmToken") ?: ""
                        PrefsManager.setPartnerFcmToken(this, p2Token)
                        PrefsManager.setPaired(this, true)

                        binding.tvPairingStatus.apply {
                            text = getString(R.string.pairing_success)
                            visibility = View.VISIBLE
                        }

                        Toast.makeText(this, getString(R.string.pairing_success), Toast.LENGTH_LONG).show()
                        binding.root.postDelayed({ finish() }, 2000)
                    }
                }
            }
    }

    /**
     * Generate a QR code bitmap from a string payload.
     */
    private fun generateQRCode(text: String, size: Int): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

