package com.jitji.todo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

class TodoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        // Existing channel settings cannot be changed after creation, so stale alarm
        // channel IDs are retired when this build creates the refreshed channel.
        listOf("jitji_todo_reminders", "jitji_todo_reminders_v2").forEach { id ->
            manager.getNotificationChannel(id)?.let {
                manager.deleteNotificationChannel(id)
            }
        }

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "할일 알람",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "잊지마 할일 알람"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setSound(alarmSound, audioAttrs)
            enableLights(true)
            lightColor = 0xFFF5F5F7.toInt()
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        // v3: alarm full-screen behavior and channel defaults refreshed.
        const val CHANNEL_ID = "jitji_todo_reminders_v3"
    }
}
