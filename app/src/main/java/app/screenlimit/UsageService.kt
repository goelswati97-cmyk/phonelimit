package app.screenlimit

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * Runs in the background (with a small ongoing notification) and adds up the time
 * the phone is switched on AND unlocked. Once today's total reaches the limit it
 * locks the phone, and keeps locking it on every unlock until midnight.
 */
class UsageService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager

    private var lastTick = 0L
    private var wasInUse = false
    private var looping = false
    private var lastShownKey = ""

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            if (looping) handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    tick()               // count the last few seconds before the screen went off
                    wasInUse = false
                    stopLoop()
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    tick()               // re-checks immediately; locks right away if time is up
                    startLoop()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        dpm = getSystemService(DevicePolicyManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        createChannels()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        lastTick = SystemClock.elapsedRealtime()
        wasInUse = isInUse()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildStatus(Store.usedMs(this), Store.limitMs(this))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_ID, notification)
        }
        if (power.isInteractive) startLoop()
        tick()
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- core logic -------------------------------------------------------

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTick).coerceIn(0L, MAX_DELTA_MS)
        lastTick = now

        var used = Store.usedMs(this)
        if (wasInUse && delta > 0) used = Store.addUsed(this, delta)

        val inUse = isInUse()
        wasInUse = inUse

        val limit = Store.limitMs(this)
        val left = limit - used

        if (left in 1..WARN_10_MS && !Store.flag(this, "warn10")) {
            Store.setFlag(this, "warn10")
            alert(WARN_ID, "10 minutes of screen time left", "Your phone will lock when today's ${Store.format(limit)} is used up.")
        }
        if (left in 1..WARN_2_MS && !Store.flag(this, "warn2")) {
            Store.setFlag(this, "warn2")
            alert(WARN_ID, "2 minutes left", "Finish up — the phone locks in about 2 minutes.")
        }

        if (used >= limit) {
            if (!Store.flag(this, "limit")) {
                Store.setFlag(this, "limit")
                alert(WARN_ID, "Daily limit reached", "Your phone will stay locked until midnight. Emergency calls still work.")
            }
            if (inUse && !inCall()) lockNow()
        }

        updateStatus(used, limit)
    }

    /** Screen is on and the lock screen has been passed. */
    private fun isInUse(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    /** Don't interrupt phone or video calls; we lock as soon as the call ends. */
    private fun inCall(): Boolean =
        audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun lockNow() {
        if (dpm.isAdminActive(LockAdminReceiver.component(this))) {
            try { dpm.lockNow() } catch (e: SecurityException) {}
        }
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        handler.postDelayed(tickRunnable, TICK_MS)
    }

    private fun stopLoop() {
        looping = false
        handler.removeCallbacks(tickRunnable)
    }

    // ---- notifications ----------------------------------------------------

    private fun createChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Today's usage", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        notifications.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Time-limit warnings", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildStatus(used: Long, limit: Long): Notification {
        val text = if (used >= limit) {
            "Limit reached — locked until midnight"
        } else {
            "${Store.format(limit - used)} left of ${Store.format(limit)}"
        }
        return Notification.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Used ${Store.format(used)} today")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    /** Refresh the ongoing notification only when the minute count changes. */
    private fun updateStatus(used: Long, limit: Long) {
        val key = "${used / 60_000L}/$limit"
        if (key == lastShownKey) return
        lastShownKey = key
        notifications.notify(STATUS_ID, buildStatus(used, limit))
    }

    private fun alert(id: Int, title: String, text: String) {
        val n = Notification.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(id, n)
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val MAX_DELTA_MS = 15_000L      // ignore gaps from a sleeping CPU
        private const val WARN_10_MS = 10 * 60_000L
        private const val WARN_2_MS = 2 * 60_000L

        private const val CH_STATUS = "status"
        private const val CH_ALERTS = "alerts"
        private const val STATUS_ID = 1
        private const val WARN_ID = 2

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, UsageService::class.java))
            } catch (e: Exception) {
                // Some phones block background starts; it will start next time the app is opened.
            }
        }
    }
}
