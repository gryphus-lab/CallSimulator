package com.example.callsimulator

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.callsimulator.databinding.ActivityInCallBinding

class InCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
        const val EXTRA_IS_OUTGOING = "extra_is_outgoing"
    }

    private lateinit var binding: ActivityInCallBinding
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var isMuted = false
    private var isSpeakerOn = false
    private var isOutgoing = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++
            val minutes = seconds / 60
            val secs = seconds % 60
            binding.callStatusText.text = String.format("%02d:%02d", minutes, secs)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(EXTRA_CALLER_NAME)
            ?: getString(R.string.default_caller_name)
        val number = intent.getStringExtra(EXTRA_CALLER_NUMBER)
            ?: getString(R.string.default_caller_number)
        isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, false)
        val photo = intent.getStringExtra(EXTRA_CALLER_PHOTO)

        binding.callerNameText.text = name
        binding.callerNumberText.text = number
        applyCallerPhoto(photo)

        if (isOutgoing) {
            binding.callStatusText.text = getString(R.string.calling)
            // Simulate "connecting" for a couple seconds then start timer
            handler.postDelayed({
                seconds = 0
                handler.post(timerRunnable)
            }, 2500)
        } else {
            seconds = 0
            handler.post(timerRunnable)
        }

        binding.endCallButton.setOnClickListener {
            endCall()
        }

        binding.muteButton.setOnClickListener {
            isMuted = !isMuted
            binding.muteButton.alpha = if (isMuted) 0.5f else 1.0f
            Toast.makeText(
                this,
                if (isMuted) "Microphone muted" else "Microphone unmuted",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            binding.speakerButton.alpha = if (isSpeakerOn) 0.5f else 1.0f
            Toast.makeText(
                this,
                if (isSpeakerOn) "Speaker on" else "Speaker off",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.keypadButton.setOnClickListener {
            Toast.makeText(this, "Keypad (simulated)", Toast.LENGTH_SHORT).show()
        }

        // Block the back button during a call - the user must use "End Call".
        // Registered via the dispatcher (the onBackPressed() override is deprecated).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally no-op.
            }
        })
    }

    /** Show the chosen caller photo in the avatar circle and as a full-screen backdrop. */
    private fun applyCallerPhoto(photo: String?) {
        if (photo.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(photo) }.getOrNull() ?: return
        runCatching {
            binding.callerAvatar.setImageURI(uri)
            binding.callerPhotoBackground.setImageURI(uri)
            if (binding.callerPhotoBackground.drawable != null) {
                binding.callerPhotoBackground.visibility = View.VISIBLE
                binding.photoScrim.visibility = View.VISIBLE
            }
        }
    }

    private fun endCall() {
        handler.removeCallbacks(timerRunnable)
        Toast.makeText(this, getString(R.string.call_ended), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }
}
