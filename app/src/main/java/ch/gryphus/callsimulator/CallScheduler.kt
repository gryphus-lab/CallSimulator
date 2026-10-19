package ch.gryphus.callsimulator

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Arms and cancels a future simulated incoming call using [AlarmManager].
 *
 * Why AlarmManager (and `setAlarmClock`) rather than WorkManager: a scheduled
 * call must ring at an *exact* wall-clock moment even under Doze. WorkManager is
 * for deferrable, constraint-based work and is explicitly inexact. `setAlarmClock`
 * is the strongest exactness guarantee, is exempt from the SCHEDULE_EXACT_ALARM
 * permission gate on Android 12+, and surfaces a system "alarm set" affordance -
 * all appropriate for a user-facing scheduled call.
 *
 * The pending schedule is also persisted (one at a time) so it can be re-armed
 * after a reboot (see [BootReceiver]); alarms do not survive a device restart.
 */
class CallScheduler(private val context: Context) {

    companion object {
        private const val PREFS = "scheduled_call"
        private const val KEY_ARMED = "armed"
        private const val KEY_NAME = "name"
        private const val KEY_NUMBER = "number"
        private const val KEY_PHOTO = "photo"
        private const val KEY_TRIGGER = "trigger_at"

        // Stable request code: a single pending scheduled call is supported, so
        // a new schedule replaces the previous one (FLAG_UPDATE_CURRENT).
        private const val REQUEST_CODE = 42
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Arm the alarm and persist it so it can be re-armed after a reboot. */
    fun schedule(call: ScheduledCall) {
        val pending = buildPendingIntent(call, PendingIntent.FLAG_UPDATE_CURRENT)
        // setAlarmClock => exact, Doze-exempt, no SCHEDULE_EXACT_ALARM needed.
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(call.triggerAtMillis, showIntent),
            pending
        )
        persist(call)
    }

    /** Cancel any armed schedule and clear its persisted copy. */
    fun cancel() {
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            alarmIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
        prefs.edit { clear() }
    }

    /** The persisted pending schedule, or null if none is armed. */
    fun pending(): ScheduledCall? {
        if (!prefs.getBoolean(KEY_ARMED, false)) return null
        val trigger = prefs.getLong(KEY_TRIGGER, 0L)
        if (trigger == 0L) return null
        return ScheduledCall(
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            number = prefs.getString(KEY_NUMBER, "").orEmpty(),
            photo = prefs.getString(KEY_PHOTO, null),
            triggerAtMillis = trigger,
        )
    }

    /**
     * Re-arm the persisted schedule if it is still in the future; otherwise
     * clear it. Called from [BootReceiver] after a reboot.
     */
    fun rearmIfPending(nowMillis: Long) {
        val call = pending() ?: return
        if (CallSchedule.shouldRearm(call.triggerAtMillis, nowMillis)) {
            schedule(call)
        } else {
            prefs.edit { clear() }
        }
    }

    private fun persist(call: ScheduledCall) {
        prefs.edit {
            putBoolean(KEY_ARMED, true)
            putString(KEY_NAME, call.name)
            putString(KEY_NUMBER, call.number)
            putString(KEY_PHOTO, call.photo)
            putLong(KEY_TRIGGER, call.triggerAtMillis)
        }
    }

    private fun alarmIntent(): Intent =
        Intent(context, CallAlarmReceiver::class.java).apply {
            action = CallAlarmReceiver.ACTION_FIRE
        }

    private fun buildPendingIntent(call: ScheduledCall, extraFlags: Int): PendingIntent {
        val intent = alarmIntent().apply {
            putExtra(CallSimulationService.EXTRA_CALLER_NAME, call.name)
            putExtra(CallSimulationService.EXTRA_CALLER_NUMBER, call.number)
            putExtra(CallSimulationService.EXTRA_CALLER_PHOTO, call.photo)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
