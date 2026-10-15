package com.example.callsimulator

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Shared behaviour for the two full-screen call activities
 * ([IncomingCallActivity] and [InCallActivity]):
 *
 * - show-over-lockscreen / turn-screen-on / full-screen window flags,
 * - rendering the caller photo in the avatar circle and as a dimmed backdrop,
 * - blocking the system back button (the user must act on the call).
 *
 * Subclasses supply the three photo views from their own ViewBinding.
 */
abstract class BaseCallActivity : AppCompatActivity() {

    /** The circular caller avatar. */
    protected abstract val avatarView: ImageView

    /** The full-screen photo backdrop (hidden until a photo is applied). */
    protected abstract val photoBackgroundView: ImageView

    /** The dim scrim drawn over the backdrop for legibility. */
    protected abstract val photoScrimView: android.view.View

    /** Apply the full-screen call window flags. Call from onCreate (after super). */
    protected fun applyCallWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    /** Show the chosen caller photo in the avatar and as a full-screen backdrop. */
    protected fun applyCallerPhoto(photo: String?) {
        if (photo.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(photo) }.getOrNull() ?: return
        runCatching {
            avatarView.setImageURI(uri)
            photoBackgroundView.setImageURI(uri)
            // Only reveal the backdrop + scrim if the image actually loaded.
            if (photoBackgroundView.drawable != null) {
                photoBackgroundView.visibility = android.view.View.VISIBLE
                photoScrimView.visibility = android.view.View.VISIBLE
            }
        }
    }

    /**
     * Block the system back button for the lifetime of this activity - during a
     * call the user must use the on-screen controls (answer/decline/end).
     */
    protected fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally no-op.
            }
        })
    }
}
