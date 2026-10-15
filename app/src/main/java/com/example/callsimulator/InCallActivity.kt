package com.example.callsimulator

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import com.example.callsimulator.databinding.ActivityInCallBinding

class InCallActivity : BaseCallActivity() {

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

    override val avatarView: ImageView get() = binding.callerAvatar
    override val photoBackgroundView: ImageView get() = binding.callerPhotoBackground
    override val photoScrimView: android.view.View get() = binding.photoScrim

    // Explicit type so the lambda body can reference timerRunnable for re-post.
    private val timerRunnable: Runnable = Runnable {
        seconds++
        binding.callStatusText.text = CallTimer.format(seconds)
        handler.postDelayed(timerRunnable, 1000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyCallWindowFlags()

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
        blockBackButton()
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
