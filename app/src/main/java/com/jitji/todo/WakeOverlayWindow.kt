package com.jitji.todo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object WakeOverlayWindow {

    private const val AUTO_DISMISS_MS = 45_000L
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun canShow(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun show(context: Context, tasks: List<Task>) {
        if (!canShow(context)) return

        handler.post {
            dismiss()

            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = buildView(appContext, tasks)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                title = "Amatda wake overlay"
            }

            runCatching {
                wm.addView(root, params)
                windowManager = wm
                overlayView = root
                handler.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
            }
        }
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        val view = overlayView ?: return
        val wm = windowManager ?: return
        runCatching { wm.removeView(view) }
        overlayView = null
        windowManager = null
    }

    private fun buildView(context: Context, tasks: List<Task>): View {
        val pending = tasks.filter { !it.isDone }
        val d = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * d).toInt()

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(232, 18, 18, 20))
            setPadding(dp(18), dp(36), dp(18), dp(36))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(36, 36, 38))
                setStroke(dp(1), Color.rgb(92, 92, 96))
            }
            elevation = dp(12).toFloat()
        }

        panel.addView(TextView(context).apply {
            text = context.getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })

        panel.addView(TextView(context).apply {
            text = if (pending.isEmpty()) {
                "할일을 추가해보세요"
            } else {
                "미완료 할일 ${pending.size}개"
            }
            setTextColor(Color.rgb(214, 214, 218))
            textSize = 14f
            setPadding(0, dp(4), 0, dp(14))
        })

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (pending.isEmpty()) {
            list.addView(makeTaskRow(context, "할일이 없어요", dp(12)))
        } else {
            pending.take(10).forEach { task ->
                list.addView(makeTaskRow(context, task.title, dp(12)))
            }
            if (pending.size > 10) {
                list.addView(makeTaskRow(context, "... +${pending.size - 10}", dp(12)))
            }
        }

        panel.addView(ScrollView(context).apply {
            addView(list)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        val actions = LinearLayout(context).apply {
            gravity = Gravity.END
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }

        actions.addView(Button(context).apply {
            text = context.getString(R.string.overlay_close)
            setOnClickListener { dismiss() }
        })
        actions.addView(Button(context).apply {
            text = context.getString(R.string.overlay_open_app)
            setOnClickListener {
                openApp(context)
                dismiss()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(8)
        })
        panel.addView(actions)

        root.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))
        return root
    }

    private fun makeTaskRow(context: Context, title: String, bottomPadding: Int): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, bottomPadding)
        }
    }

    private fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("lockscreen_wake", true)
        }
        runCatching { context.startActivity(intent) }
    }
}
