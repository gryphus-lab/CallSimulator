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
 *    [ACTION_INCOMING]) is started. It owns the call lifecycle and posts the
 *    high-priority full-screen-intent (FSI) notification. This is the
 *    OS-sanctioned LOCK-SCREEN path: when the device is locked or the screen is
 *    off, the FSI promotes itself to the full-screen caller UI - PROVIDED the
 *    app currently holds full-screen-intent access. On Android 14+ that access
 *    is a user-revocable special permission (canUseFullScreenIntent()); if it is
 *    not granted the system silently demotes the FSI to a shade/heads-up
 *    notification (the "it only shows in the notification bar on the lock
 *    screen" symptom). MainActivity requests that access up front so this path
 *    works. There is nothing more the receiver can do at fire time to force a
 *    full-screen takeover over the keyguard - the FSI grant is the mechanism.
 *
 * 2. [IncomingCallActivity] is launched directly. This covers the
 *    screen-on + unlocked case, where Android would otherwise demote the FSI to
 *    a heads-up banner. Because the schedule is armed with
 *    `AlarmManager.setAlarmClock()`, the app receives a temporary
 *    background-activity-launch allowance when the alarm fires, so starting the
 *    activity here is permitted. On a locked device this direct start may be
 *    blocked by background-activity-launch / keyguard rules - that is fine and
 *    expected: path 1's FSI is what brings up the caller over the lock screen,
 *    so the direct start is best-effort and its failure is caught. The activity
 *    is single-instance, so the two paths never double-stack.
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
