package com.example.callsimulator

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.callsimulator.databinding.ActivityIncomingCallBinding

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
    }

    private lateinit var binding: ActivityIncomingCallBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen + show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(EXTRA_CALLER_NAME)
            ?: getString(R.string.default_caller_name)
        val number = intent.getStringExtra(EXTRA_CALLER_NUMBER)
            ?: getString(R.string.default_caller_number)
        val photo = intent.getStringExtra(EXTRA_CALLER_PHOTO)

        binding.callerNameText.text = name
        binding.callerNumberText.text = number
        applyCallerPhoto(photo)

        binding.answerButton.setOnClickListener {
            stopRingtoneAndVibrate()
            // Stop the service notification
            stopService(Intent(this, CallSimulationService::class.java))

            val inCallIntent = Intent(this, InCallActivity::class.java).apply {
                putExtra(InCallActivity.EXTRA_CALLER_NAME, name)
                putExtra(InCallActivity.EXTRA_CALLER_NUMBER, number)
                putExtra(InCallActivity.EXTRA_CALLER_PHOTO, photo)
                putExtra(InCallActivity.EXTRA_IS_OUTGOING, false)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(inCallIntent)
            finish()
        }

        binding.declineButton.setOnClickListener {
            stopRingtoneAndVibrate()
            stopService(Intent(this, CallSimulationService::class.java))
            finish()
        }

        startRingtoneAndVibrate()
    }

    /** Show the chosen caller photo in the avatar circle and as a full-screen backdrop. */
    private fun applyCallerPhoto(photo: String?) {
        if (photo.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(photo) }.getOrNull() ?: return
        runCatching {
            binding.callerAvatar.setImageURI(uri)
            binding.callerPhotoBackground.setImageURI(uri)
            // Only reveal the backdrop + scrim if the image actually loaded.
            if (binding.callerPhotoBackground.drawable != null) {
                binding.callerPhotoBackground.visibility = View.VISIBLE
                binding.photoScrim.visibility = View.VISIBLE
            }
        }
    }

    private fun startRingtoneAndVibrate() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 1000)
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
        }
    }

    private fun stopRingtoneAndVibrate() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopRingtoneAndVibrate()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent accidental back during incoming call simulation
        // User must decline or answer
    }
}
