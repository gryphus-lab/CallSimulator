package ch.gryphus.callsimulator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import ch.gryphus.callsimulator.databinding.ActivityInCallBinding

/**
 * The in-call screen. It is a *view* onto [CallSimulationService], which owns
 * the call: the activity binds to the service, reads the authoritative call
 * start time, and renders the duration. Because the call lives in the
 * foreground service, pressing Home (or otherwise backgrounding the UI) keeps
 * the call running - the ongoing notification taps straight back here.
 */
class InCallActivity : BaseCallActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
        const val EXTRA_IS_OUTGOING = "extra_is_outgoing"

        // Simulated "connecting" delay for an outgoing call before it connects.
        private const val OUTGOING_CONNECT_DELAY_MS = 2500L
    }

    private lateinit var binding: ActivityInCallBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isSpeakerOn = false
    private var isOutgoing = false

    private var service: CallSimulationService? = null
    private var bound = false

    override val avatarView: ImageView get() = binding.callerAvatar
    override val photoBackgroundView: ImageView get() = binding.callerPhotoBackground
    override val photoScrimView: android.view.View get() = binding.photoScrim

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? CallSimulationService.LocalBinder)?.service
            bound = true
            // For an outgoing call, schedule the "connected" transition on the
            // service (idempotent) so the timer's source of truth is the
            // service, not this activity.
            if (isOutgoing && service?.isConnected == false) {
                handler.postDelayed({ service?.markConnected() }, OUTGOING_CONNECT_DELAY_MS)
            }
            startTicking()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    // Drives the on-screen duration from the service's authoritative start time.
    private val tick: Runnable = object : Runnable {
        override fun run() {
            renderDuration()
            handler.postDelayed(this, 1000)
        }
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
        binding.callStatusText.text =
            if (isOutgoing) getString(R.string.calling) else CallTimer.format(0)

        // Ensure the call is owned by the foreground service. Starting it here
        // (idempotent) covers the outgoing path and re-entry after process
        // death; the incoming path already started it on answer.
        val ongoing = Intent(this, CallSimulationService::class.java).apply {
            action = CallSimulationService.ACTION_ONGOING
            putExtra(CallSimulationService.EXTRA_CALLER_NAME, name)
            putExtra(CallSimulationService.EXTRA_CALLER_NUMBER, number)
            putExtra(CallSimulationService.EXTRA_CALLER_PHOTO, photo)
            putExtra(CallSimulationService.EXTRA_IS_OUTGOING, isOutgoing)
        }
        ContextCompat.startForegroundService(this, ongoing)

        binding.endCallButton.setOnClickListener { endCall() }

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

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, CallSimulationService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        // Re-entering from the notification/recents: refresh immediately so the
        // duration is correct even before the next tick.
        startTicking()
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        handler.removeCallbacks(tick)
    }

    private fun startTicking() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun renderDuration() {
        val svc = service
        if (svc == null) {
            // Not yet bound; leave the initial text as-is.
            return
        }
        if (svc.isConnected) {
            val seconds = (svc.elapsedCallMillis() / 1000L).toInt()
            binding.callStatusText.text = CallTimer.format(seconds)
        } else {
            binding.callStatusText.text = getString(R.string.calling)
        }
    }

    private fun endCall() {
        handler.removeCallbacks(tick)
        // Tearing down the service removes the ongoing notification and ends
        // the call everywhere.
        if (bound) {
            unbindService(connection)
            bound = false
        }
        stopService(Intent(this, CallSimulationService::class.java))
        Toast.makeText(this, getString(R.string.call_ended), Toast.LENGTH_SHORT).show()
        finish()
    }
}
