package ch.gryphus.callsimulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when a scheduled call's alarm elapses, and makes the call ring exactly
 * like a live incoming call - opening the full-screen caller UI by default, not
 * just a heads-up banner.
 *
 * Two things happen on fire:
 *
 * 1. The incoming-call foreground service ([CallSimulationService] with
 *    [ACTION_INCOMING]) is started. It owns the call lifecycle, posts the
 *    high-priority full-screen-intent notification, and provides the
 *    background-survival + lock-screen path (the FSI promotes itself to full
 *    screen when the device is locked or the screen is off).
 *
 * 2. [IncomingCallActivity] is launched directly. This is the key to the
 *    "full screen by default" behaviour: when the device is unlocked with the
 *    screen on and the app is in the background, Android 10+ demotes a
 *    full-screen-intent notification to a heads-up banner ("the bar on top").
 *    Because the schedule is armed with `AlarmManager.setAlarmClock()`, the app
 *    receives a temporary background-activity-launch allowance when the alarm
 *    fires, so starting the activity here is permitted and reliably shows the
 *    caller full screen. The activity is single-instance, so this never
 *    double-stacks with the notification's full-screen intent.
 */
class CallAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "ch.gryphus.callsimulator.ACTION_FIRE_SCHEDULED_CALL"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return

        val name = intent.getStringExtra(CallSimulationService.EXTRA_CALLER_NAME)
        val number = intent.getStringExtra(CallSimulationService.EXTRA_CALLER_NUMBER)
        val photo = intent.getStringExtra(CallSimulationService.EXTRA_CALLER_PHOTO)

        // 1. Own the call lifecycle + post the full-screen-intent notification.
        val serviceIntent = Intent(context, CallSimulationService::class.java).apply {
            action = CallSimulationService.ACTION_INCOMING
            putExtra(CallSimulationService.EXTRA_CALLER_NAME, name)
            putExtra(CallSimulationService.EXTRA_CALLER_NUMBER, number)
            putExtra(CallSimulationService.EXTRA_CALLER_PHOTO, photo)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // 2. Open the full-screen caller UI directly (allowed by the
        //    setAlarmClock BAL grace window), so a scheduled call doesn't just
        //    show a heads-up banner when the screen is on and unlocked.
        val callIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHOTO, photo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(callIntent) }

        // One-shot schedule: clear the persisted copy now that it has fired.
        CallScheduler(context).cancel()
    }
}
