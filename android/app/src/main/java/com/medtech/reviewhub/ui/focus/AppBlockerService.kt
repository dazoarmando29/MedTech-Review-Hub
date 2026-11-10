package com.medtech.reviewhub.ui.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.medtech.reviewhub.MainActivity
import com.medtech.reviewhub.ReviewHubApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Focus-mode distraction monitor (Android-only flagship feature).
 *
 * IMPORTANT — honest platform limitation: Android does NOT guarantee absolute
 * blocking of other apps. When the user grants Usage Access, this foreground
 * service polls UsageStatsManager for the current foreground app while a focus
 * session is active, and — if the app is on the user-chosen distraction list —
 * shows a blocking overlay (SYSTEM_ALERT_WINDOW) with a mascot message and
 * "Return to study" / "5 more minutes" / emergency-pause options. Overlay
 * windows can be dismissed by the system or bypassed (e.g. via recents), and
 * some devices restrict usage stats. When permissions are missing the app
 * falls back to focus reminder notifications and an explanatory banner.
 *
 * No hidden monitoring: this service only runs while the user starts a focus
 * session, only reads usage stats after an explicit, explained opt-in, and can
 * be disabled or uninstalled at any time from Settings.
 */
class AppBlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var overlayView: View? = null
    private var blockedPackages: Set<String> = emptySet()
    private var lastInterruptionAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            detectForegroundApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                FocusRuntime.paused = true
                removeOverlay()
                updateNotification("Focus session paused")
                return START_STICKY
            }
            else -> {}
        }
        val notification = buildNotification("Focus session running — stay on track!")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        FocusRuntime.serviceActive = true
        FocusRuntime.paused = false
        scope.launch {
            blockedPackages = ReviewHubApp.container?.focusRepository?.blockedAppsOnce()
                ?.map { it.packageName }?.toSet() ?: emptySet()
        }
        handler.post(pollRunnable)
        return START_STICKY
    }

    private fun detectForegroundApp() {
        if (FocusRuntime.paused || blockedPackages.isEmpty()) return
        if (!hasUsageAccess()) return
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000, now)
        val foreground = stats
            .filter { it.lastTimeUsed > now - 5_000 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?: return
        if (foreground in blockedPackages && foreground != packageName && now - lastInterruptionAt > 10_000) {
            lastInterruptionAt = now
            FocusRuntime.interruptions++
            scope.launch { showOverlay(foreground) }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return stats != null && stats.isNotEmpty()
    }

    private suspend fun showOverlay(packageName: String) {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60F172A"))
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val message = TextView(this).apply {
            text = "Meow! Back to study, future MLS!\n\n$appName can wait — your flashcards can't. " +
                "(Mochi agrees: review muna bago scroll.)"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val returnBtn = Button(this).apply {
            text = "Return to study"
            setOnClickListener { removeOverlay() }
        }
        val snoozeBtn = Button(this).apply {
            text = "5 more minutes"
            setOnClickListener {
                FocusRuntime.snoozeRequests++
                removeOverlay()
            }
        }
        val pauseBtn = Button(this).apply {
            text = "Emergency pause"
            setOnClickListener {
                FocusRuntime.paused = true
                removeOverlay()
            }
        }
        container.addView(message)
        container.addView(returnBtn)
        container.addView(snoozeBtn)
        container.addView(pauseBtn)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            wm.addView(container, params)
            overlayView = container
        }
    }

    private fun removeOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let { runCatching { wm.removeView(it) } }
        overlayView = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus session",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Reminders while a Pomodoro focus session is running." }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MedTech Focus Mode")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        removeOverlay()
        scope.cancel()
        FocusRuntime.serviceActive = false
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "focus_session"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.medtech.reviewhub.focus.STOP"
        const val ACTION_PAUSE = "com.medtech.reviewhub.focus.PAUSE"
        private const val POLL_INTERVAL_MS = 2_000L
    }
}

/** Simple shared runtime flags between the service and the UI (manual DI). */
object FocusRuntime {
    @Volatile var serviceActive: Boolean = false
    @Volatile var paused: Boolean = false
    @Volatile var interruptions: Int = 0
    @Volatile var snoozeRequests: Int = 0
}
