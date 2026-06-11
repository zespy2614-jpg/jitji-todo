package com.jitji.todo

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

object WakeOverlayWindow {

    private const val AUTO_DISMISS_MS = 1_500L
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun canShow(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun showLaunchBridge(context: Context, onReady: () -> Unit): Boolean {
        if (!canShow(context)) return false

        handler.post {
            dismiss()

            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bridge = View(appContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                title = "Amatda launch bridge"
            }

            runCatching {
                wm.addView(bridge, params)
                windowManager = wm
                overlayView = bridge
                onReady()
                handler.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
            }.onFailure {
                onReady()
            }
        }
        return true
    }

    fun dismiss() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        runCatching { wm.removeView(view) }
        overlayView = null
        windowManager = null
    }
}
