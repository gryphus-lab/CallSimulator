package ch.gryphus.callsimulator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Owns the call lifecycle so a call survives the app being backgrounded.
 *
 * Two phases, selected by the start [Intent]'s action:
 *
 * - [ACTION_INCOMING]: ringing. Posts a high-priority full-screen-intent
 *   notification that launches [IncomingCallActivity].
 * - [ACTION_ONGOING]: an active (answered or outgoing-connected) call. The
 *   service becomes the single source of truth for the call: it records the
 *   start time, posts an ongoing call notification with a running chronometer
 *   and an "End call" action, and keeps itself in the foreground. Pressing Home
 *   therefore does NOT end the call - the service keeps running and the
 *   notification taps back into [InCallActivity], exactly like the native
 *   Phone app.
 *
 * [InCallActivity] binds to the service to read the authoritative call start
 * time, so re-entering the call screen always shows the correct elapsed
 * duration regardless of how long the UI was in the background.
 */
class CallSimulationService : Service() {

    companion object {
        const val CHANNEL_ID = "call_simulation_channel"
        const val ONGOING_CHANNEL_ID = "ongoing_call_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_INCOMING = "ch.gryphus.callsimulator.ACTION_INCOMING"
        const val ACTION_ONGOING = "ch.gryphus.callsimulator.ACTION_ONGOING"
        const val ACTION_END_CALL = "ch.gryphus.callsimulator.ACTION_END_CALL"

        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
        const val EXTRA_IS_OUTGOING = "extra_is_outgoing"
    }

    /** Lets the in-call UI read the authoritative call state from the service. */
    inner class LocalBinder : Binder() {
        val service: CallSimulationService get() = this@CallSimulationService
    }

    private val binder = LocalBinder()

    /**
     * elapsedRealtime() baseline for the active call, or 0 while not in an
     * active call. Using elapsedRealtime keeps the duration correct across UI
     * backgrounding and is monotonic (unaffected by wall-clock changes).
     */
    @Volatile
    var callStartElapsedRealtime: Long = 0L
        private set

    /** True once an outgoing call has "connected" and the timer should run. */
    @Volatile
    var isConnected: Boolean = false
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ONGOING -> startOngoing(intent)
            ACTION_END_CALL -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startIncoming(intent)
        }
        return START_NOT_STICKY
    }

    // ---- Ringing phase -----------------------------------------------------

    private fun startIncoming(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_CALLER_NAME)
            ?: getString(R.string.default_caller_name)
        val number = intent?.getStringExtra(EXTRA_CALLER_NUMBER)
            ?: getString(R.string.default_caller_number)
        val photo = intent?.getStringExtra(EXTRA_CALLER_PHOTO)

        createIncomingChannel()

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHOTO, photo)
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.incoming_call))
            .setContentText("$name ($number)")
            .setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ---- Active-call phase -------------------------------------------------

    private fun startOngoing(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_CALLER_NAME)
            ?: getString(R.string.default_caller_name)
        val number = intent.getStringExtra(EXTRA_CALLER_NUMBER)
            ?: getString(R.string.default_caller_number)
        val photo = intent.getStringExtra(EXTRA_CALLER_PHOTO)
        val isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, false)

        // Start (or keep) the authoritative timer. Only set it once so a second
        // ACTION_ONGOING (e.g. redelivery) doesn't reset an in-progress call.
        if (callStartElapsedRealtime == 0L) {
            // Outgoing calls show "Calling…" first and connect after a short
            // delay; the activity flips isConnected via markConnected(). An
            // answered incoming call is connected immediately.
            isConnected = !isOutgoing
            callStartElapsedRealtime =
                if (isConnected) SystemClock.elapsedRealtime() else 0L
        }

        createOngoingChannel()
        startForeground(NOTIFICATION_ID, buildOngoingNotification(name, number, photo))
    }

    /** Called by the UI when an outgoing call transitions from ringing to connected. */
    fun markConnected() {
        if (!isConnected) {
            isConnected = true
            callStartElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    private fun buildOngoingNotification(
        name: String,
        number: String,
        photo: String?,
    ): Notification {
        val contentIntent = Intent(this, InCallActivity::class.java).apply {
            putExtra(InCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(InCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(InCallActivity.EXTRA_CALLER_PHOTO, photo)
            putExtra(InCallActivity.EXTRA_IS_OUTGOING, !isConnected)
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPending = PendingIntent.getActivity(
            this, 1, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, CallSimulationService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endPending = PendingIntent.getService(
            this, 2, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(number)
            .setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_call_end, getString(R.string.end_call), endPending)

        if (isConnected) {
            // Live-updating duration in the notification shade, like the native
            // dialer, driven by the service's start time (no per-second posts).
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsedCallMillis())
                .setShowWhen(true)
        } else {
            builder.setContentText("${getString(R.string.calling)}  $number")
        }
        return builder.build()
    }

    /** Milliseconds since the active call connected, or 0 if not yet connected. */
    fun elapsedCallMillis(): Long =
        if (callStartElapsedRealtime == 0L) 0L
        else SystemClock.elapsedRealtime() - callStartElapsedRealtime

    private fun createIncomingChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null) // ringtone handled in the activity
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createOngoingChannel() {
        val channel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            getString(R.string.ongoing_channel_name),
            NotificationManager.IMPORTANCE_LOW // silent, persistent
        ).apply {
            description = getString(R.string.ongoing_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        callStartElapsedRealtime = 0L
        isConnected = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
