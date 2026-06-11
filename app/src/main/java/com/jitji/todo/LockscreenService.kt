package com.jitji.todo

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

class LockscreenService : Service() {

    private lateinit var tasksLive: LiveData<List<Task>>
    private val mainHandler = Handler(Looper.getMainLooper())
    private var latestTasks: List<Task> = emptyList()
    private var priorityUiSuppressUntilElapsed = 0L
    private val observer = Observer<List<Task>> { list ->
        latestTasks = list
        LockscreenNotification.update(this, list)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> openTodoAfterScreenOn(ctx)
                Intent.ACTION_USER_PRESENT -> openTodoUnlessPrioritySystemUi(ctx)
                else -> return
            }
        }
    }
    private var screenReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        LockscreenNotification.ensureChannel(this)
        val initial = LockscreenNotification.build(this, emptyList())

        // Android 12+: 백그라운드에서 시스템이 서비스를 되살릴 때 startForeground가
        // ForegroundServiceStartNotAllowedException을 던지며 크래시 루프가 생긴다.
        // 실패하면 조용히 종료하고 워치독(정확 알람 = FGS 시작 면제 경로)에 재시작을 맡긴다.
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    LockscreenNotification.NOTIFICATION_ID,
                    initial,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(LockscreenNotification.NOTIFICATION_ID, initial)
            }
        }.isSuccess

        if (!started) {
            foregroundStarted = false
            ServiceWatchdog.scheduleImmediateRestart(applicationContext, delayMs = 5_000L)
            stopSelf()
            return
        }
        foregroundStarted = true

        tasksLive = TaskRepository(applicationContext).observeAll()
        tasksLive.observeForever(observer)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    private var foregroundStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY는 백그라운드 자동재시작 → FGS 시작 불가 크래시 루프를 만든다.
        // 재시작은 onTaskRemoved/onDestroy의 워치독 정확 알람으로만 수행.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceWatchdog.scheduleImmediateRestart(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (::tasksLive.isInitialized) {
            tasksLive.removeObserver(observer)
        }
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        // startForeground 실패로 종료된 경우엔 onCreate에서 이미 5초 후 재시도를
        // 예약했으므로 즉시 재시작을 또 걸지 않는다 (핫루프 방지).
        if (foregroundStarted) {
            ServiceWatchdog.scheduleImmediateRestart(applicationContext)
        }
        super.onDestroy()
    }

    private fun openTodoAfterScreenOn(context: Context) {
        val appContext = applicationContext
        if (shouldSkipForPrioritySystemUi(appContext)) return

        mainHandler.postDelayed({
            if (!shouldSkipForPrioritySystemUi(appContext)) {
                openTodoOnLockscreen(context)
            }
        }, SCREEN_ON_OPEN_DELAY_MS)
    }

    private fun openTodoUnlessPrioritySystemUi(context: Context) {
        if (!shouldSkipForPrioritySystemUi(applicationContext)) {
            openTodoOnLockscreen(context)
        }
    }

    private fun shouldSkipForPrioritySystemUi(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < priorityUiSuppressUntilElapsed) return true

        if (isPrioritySystemUiActive(context)) {
            priorityUiSuppressUntilElapsed = now + PRIORITY_UI_SUPPRESS_MS
            return true
        }
        return false
    }

    private fun isPrioritySystemUiActive(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return isCallAudioMode(audio) || isAlarmPlaybackActive(audio)
    }

    private fun isCallAudioMode(audio: AudioManager): Boolean {
        return when (audio.mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    }

    private fun isAlarmPlaybackActive(audio: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            audio.activePlaybackConfigurations.any { config ->
                config.audioAttributes.usage in setOf(
                    AudioAttributes.USAGE_ALARM,
                    AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                )
            }
        }.getOrDefault(false)
    }

    private fun openTodoOnLockscreen(context: Context) {
        val appContext = applicationContext
        startMainActivity(appContext)

        if (WakeOverlayWindow.showLaunchBridge(context) { startMainActivity(appContext) }) {
            mainHandler.postDelayed({ startMainActivity(appContext) }, 40L)
            mainHandler.postDelayed({ startMainActivity(appContext) }, 120L)
            return
        }

        mainHandler.postDelayed({ startMainActivity(appContext) }, 40L)
        mainHandler.postDelayed({ startMainActivity(appContext) }, 120L)
    }

    private fun startMainActivity(context: Context) {
        val i = Intent(context, MainActivity::class.java).apply {
            addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra("lockscreen_wake", true)
        }
        runCatching { context.startActivity(i) }
    }

    companion object {
        private const val SCREEN_ON_OPEN_DELAY_MS = 160L
        private const val PRIORITY_UI_SUPPRESS_MS = 120_000L

        fun start(context: Context) {
            // Android 12+: 백그라운드에서 호출되면 ForegroundServiceStartNotAllowedException이
            // 동기로 던져진다 — 호출부가 어디든 크래시하지 않도록 여기서 흡수한다.
            runCatching {
                val intent = Intent(context, LockscreenService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
