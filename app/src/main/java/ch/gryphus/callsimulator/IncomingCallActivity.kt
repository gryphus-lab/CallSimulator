package ch.gryphus.callsimulator

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.ImageView
import ch.gryphus.callsimulator.databinding.ActivityIncomingCallBinding

class IncomingCallActivity : BaseCallActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
    }

    private lateinit var binding: ActivityIncomingCallBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override val avatarView: ImageView get() = binding.callerAvatar
    override val photoBackgroundView: ImageView get() = binding.callerPhotoBackground
    override val photoScrimView: android.view.View get() = binding.photoScrim

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyCallWindowFlags()

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

        // Block the back button during an incoming call - answer or decline only.
        blockBackButton()
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
            // minSdk 26 (>= O), so VibrationEffect is always available.
            val pattern = longArrayOf(0, 1000, 1000)
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
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
}
