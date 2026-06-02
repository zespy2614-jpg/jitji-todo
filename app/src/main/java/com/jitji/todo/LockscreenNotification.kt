package com.jitji.todo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object LockscreenNotification {

    const val CHANNEL_ID = "jitji_todo_lockscreen_v2"
    const val NOTIFICATION_ID = 9001
    private const val WAKE_CHANNEL_ID = "jitji_todo_lockscreen_wake_v1"
    private const val WAKE_NOTIFICATION_ID = 9002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "잠금화면 할일",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "잠금화면에 계속 표시되는 할일 목록"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun ensureWakeChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(WAKE_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            WAKE_CHANNEL_ID,
            "Lock screen wake",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Opens the todo list when the screen turns on"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(context: Context, tasks: List<Task>): Notification {
        ensureChannel(context)

        val pending = tasks.filter { !it.isDone }
        val visible = pending.take(7)
        val body = if (visible.isEmpty()) {
            "할일을 추가해보세요"
        } else {
            buildString {
                visible.forEachIndexed { idx, task ->
                    append("- ")
                    append(task.title)
                    if (idx < visible.lastIndex) append('\n')
                }
                if (pending.size > visible.size) {
                    append("\n... +${pending.size - visible.size}")
                }
            }
        }

        val title = if (pending.isEmpty()) {
            "아맞다"
        } else {
            "아맞다 (${pending.size}개)"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(visible.firstOrNull()?.title ?: "할일을 추가해보세요")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(contentPi)
            .build()
    }

    fun update(context: Context, tasks: List<Task>) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, tasks))
    }

    fun showWakeFullscreen(context: Context, tasks: List<Task>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureWakeChannel(context)

        val pending = tasks.filter { !it.isDone }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("lockscreen_wake", true)
        }
        val openPi = PendingIntent.getActivity(
            context,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.app_name)
        val text = pending.firstOrNull()?.title ?: title
        val notification = NotificationCompat.Builder(context, WAKE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(WAKE_NOTIFICATION_ID, notification)
        }
    }
}
