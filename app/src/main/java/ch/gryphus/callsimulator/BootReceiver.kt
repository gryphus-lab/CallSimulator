package ch.gryphus.callsimulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms a persisted scheduled call after a reboot. AlarmManager alarms do not
 * survive a device restart, so a still-future schedule must be registered again
 * on BOOT_COMPLETED; an already-elapsed one is cleared.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            CallScheduler(context).rearmIfPending(System.currentTimeMillis())
        }
    }
}
