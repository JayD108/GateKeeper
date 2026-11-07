package com.gatekeeper.alert.ui.alarm

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.gatekeeper.alert.R
import com.gatekeeper.alert.databinding.ActivityAlarmBinding
import com.gatekeeper.alert.util.Constants
import com.gatekeeper.alert.util.PrefsManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Full-screen alarm activity — the "Persistent Siren" for P2.
 *
 * Behavior:
 *   - Shows over lock screen (showWhenLocked, turnScreenOn)
 *   - Plays a loud, looping alarm sound at max volume
 *   - Vibrates continuously
 *   - Displays a giant "GATE UNLOCKED / GOT IT!" button
 *   - On tap: silences alarm, sends acknowledgment back to P1
 *   - Dismisses the alarm notification
 */
class AlarmActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlarmActivity"
    }

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        setupLockScreenFlags()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start alarm
        startAlarmSound()
        startVibration()

        // Acknowledge button
        binding.btnAcknowledge.setOnClickListener {
            acknowledgeAlarm()
        }
    }

    /**
     * Configure window flags to show over lock screen and keep screen on.
     */
    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Play a loud, looping alarm sound at maximum volume.
     */
    private fun startAlarmSound() {
        try {
            // Force volume to max
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Use the default alarm ringtone
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            Log.i(TAG, "Alarm sound started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
        }
    }

    /**
     * Start continuous vibration pattern.
     */
    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    /**
     * User tapped "GOT IT" — silence alarm and send acknowledgment to P1.
     */
    private fun acknowledgeAlarm() {
        Log.i(TAG, "Alarm acknowledged by P2")

        // Stop alarm
        stopAlarm()

        // Clear the alarm notification
        NotificationManagerCompat.from(this).cancel(Constants.NOTIF_ID_ALARM)

        // Send acknowledgment to Firestore
        val ackData = hashMapOf(
            "type" to "ack",
            "timestamp" to FieldValue.serverTimestamp(),
            "sessionId" to PrefsManager.getSessionId(this)
        )

        firestore.collection("alerts")
            .add(ackData)
            .addOnSuccessListener {
                Log.i(TAG, "Acknowledgment sent to Firestore")
                Toast.makeText(this, getString(R.string.alarm_acknowledged), Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send ack, trying SMS fallback", e)
                sendAckSms()
            }

        finish()
    }

    /**
     * SMS fallback for acknowledgment.
     */
    private fun sendAckSms() {
        val phone = PrefsManager.getPartnerPhone(this)
        if (phone.isNullOrEmpty()) return
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, Constants.SMS_CMD_ACK, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS ack failed", e)
        }
    }

    /**
     * Stop all alarm audio and vibration.
     */
    private fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    /**
     * Prevent back button from dismissing alarm — must use the acknowledge button.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Do nothing — user must tap "GOT IT" to dismiss
        Toast.makeText(this, "Please tap 'GATE UNLOCKED / GOT IT' to dismiss", Toast.LENGTH_SHORT).show()
    }
}

