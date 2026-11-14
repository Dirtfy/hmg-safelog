package com.hmg.safelog.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Live mode screen for the Pleos Connect Emulator. All logic lives in [LiveSession]; this activity only shows
 * the session's view, so a new instance (Pleos recreates it after a driving restriction) keeps the same state.
 *
 * adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.LiveActivity [--ez route true] [--ez frames true]
 * Driver voice reply (no microphone on the emulator, so the recognized text is handed in):
 *   adb shell am broadcast -a com.hmg.safelog.poc.VOICE --es text "응, 안내해 줘" -p com.hmg.safelog.poc
 */
class LiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LiveSession.start(this, intent.getBooleanExtra("route", false), intent.getBooleanExtra("frames", false))
        val view = LiveSession.view
        (view.parent as? ViewGroup)?.removeView(view)
        // Pleos draws its vehicle status strip over the top-left corner in full screen; optional inset
        val top = intent.getIntExtra("top_inset", 0)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF0E1218.toInt())
            setPadding(0, top, 0, 0)
            addView(view)
        })
        if (intent.getBooleanExtra("fullscreen", false)) hideSystemBars()
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            Log.i(TAG, "VIEW_BOUNDS ${loc[0]} ${loc[1]} ${v.width} ${v.height}")
        }
        Log.i(TAG, "ACTIVITY_CREATED")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "BROUGHT_TO_FRONT")
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private companion object {
        const val TAG = "SafeLog"
    }
}
