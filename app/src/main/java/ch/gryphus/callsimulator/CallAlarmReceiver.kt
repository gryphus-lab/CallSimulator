package ch.gryphus.callsimulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when a scheduled call's alarm elapses. Starts the existing incoming-call
 * foreground service ([CallSimulationService] with [ACTION_INCOMING]), so a
 * scheduled call rings exactly like a live one - full-screen UI, ongoing
 * notification, and the background-survival behaviour already in place.
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

        val serviceIntent = Intent(context, CallSimulationService::class.java).apply {
            action = CallSimulationService.ACTION_INCOMING
            putExtra(CallSimulationService.EXTRA_CALLER_NAME, name)
            putExtra(CallSimulationService.EXTRA_CALLER_NUMBER, number)
            putExtra(CallSimulationService.EXTRA_CALLER_PHOTO, photo)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // One-shot schedule: clear the persisted copy now that it has fired.
        CallScheduler(context).cancel()
    }
}
