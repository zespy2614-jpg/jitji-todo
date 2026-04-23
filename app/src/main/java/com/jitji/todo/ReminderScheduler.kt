package com.jitji.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ReminderScheduler {

    fun schedule(context: Context, task: Task) {
        val dueAt = task.dueAt ?: return
        if (task.isDone) return
        if (dueAt <= System.currentTimeMillis()) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, task.id, task.title, task.memo)

        // 알람을 열면 MainActivity로 (AlarmClockInfo 용 showIntent)
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPi = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    // setAlarmClock: Doze/절전 완전 우회, 안드로이드가 "알람"으로 인식
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(dueAt, showPi), pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pi)
                }
            } else {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(dueAt, showPi), pi)
            }
        } catch (se: SecurityException) {
            // 권한 없으면 inexact로 폴백
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pi)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context, taskId, "", ""))
    }

    private fun buildPendingIntent(
        context: Context,
        taskId: Long,
        title: String,
        memo: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MEMO, memo)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }
}
