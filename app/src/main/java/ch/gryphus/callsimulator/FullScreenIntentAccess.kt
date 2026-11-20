package ch.gryphus.callsimulator

import android.os.Build

/**
 * Pure decision logic for the Android 14+ (API 34, UPSIDE_DOWN_CAKE) full-screen
 * intent (FSI) permission.
 *
 * Why this matters for a scheduled call on a LOCKED screen: the lock-screen path
 * is the high-priority FSI notification posted by [CallSimulationService]. On
 * Android 13 and below, apps holding `USE_FULL_SCREEN_INTENT` get the FSI granted
 * at install time, so it promotes itself to the full-screen caller UI when the
 * device is locked. On Android 14+, that install-time grant was revoked for apps
 * that are not the default dialer / default alarm app: `USE_FULL_SCREEN_INTENT`
 * becomes a user-revocable special app access
 * (`NotificationManager.canUseFullScreenIntent()`). If it is NOT granted, the
 * system silently demotes the FSI to an ordinary heads-up / shade notification -
 * i.e. the call only shows up "in the notification bar" instead of taking over
 * the lock screen.
 *
 * This helper isolates the *decision* ("do we need to send the user to the FSI
 * settings screen?") from the Android glue that reads `canUseFullScreenIntent()`
 * and launches the settings Intent, so the decision is unit-testable on the JVM.
 */
object FullScreenIntentAccess {

    /** First SDK level where FSI is a runtime-granted special access. */
    const val FSI_GATED_SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE // 34

    /**
     * Whether the app should prompt the user to allow full-screen intents.
     *
     * @param sdkInt the running platform level ([Build.VERSION.SDK_INT]).
     * @param canUseFullScreenIntent the current value of
     *   `NotificationManager.canUseFullScreenIntent()` on API 34+; ignored below
     *   that level (pass `true` / any value - it is not consulted).
     *
     * Returns true only on API 34+ when the permission is currently NOT granted.
     * Below API 34 the permission is implicit, so there is never anything to ask.
     */
    fun shouldRequestAccess(sdkInt: Int, canUseFullScreenIntent: Boolean): Boolean =
        sdkInt >= FSI_GATED_SDK && !canUseFullScreenIntent
}
