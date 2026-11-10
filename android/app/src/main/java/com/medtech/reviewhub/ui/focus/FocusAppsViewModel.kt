package com.medtech.reviewhub.ui.focus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.repo.FocusRepository
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.domain.model.FocusSession
import com.medtech.reviewhub.domain.model.InstalledAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Focus-mode side state: distraction app picker (PackageManager launcher
 * query), permission status, blocker service control, session stats.
 */
class FocusAppsViewModel(
    private val context: Context,
    private val repository: FocusRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    data class AppsState(
        val apps: List<InstalledAppInfo> = emptyList(),
        val blocked: Set<String> = emptySet(),
        val usageGranted: Boolean = false,
        val overlayGranted: Boolean = false,
        val focusMinutesToday: Int = 0,
        val completedSessions: Int = 0,
        val recentSessions: List<FocusSession> = emptyList(),
        val customWork: Int = 25,
        val customBreak: Int = 5
    )

    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val apps = repository.installedLauncherApps()
            val blocked = repository.blockedAppsOnce().map { it.packageName }.toSet()
            val s = settings.current()
            val todayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            _state.value = AppsState(
                apps = apps,
                blocked = blocked,
                usageGranted = hasUsageAccess(),
                overlayGranted = Settings.canDrawOverlays(context),
                focusMinutesToday = repository.focusMinutesToday(todayStart),
                completedSessions = repository.recentSessions(100).count { it.completedMinutes >= it.plannedMinutes },
                recentSessions = repository.recentSessions(10),
                customWork = s.customWorkMinutes,
                customBreak = s.customBreakMinutes
            )
        }
    }

    fun refreshPermissions() {
        _state.value = _state.value.copy(
            usageGranted = hasUsageAccess(),
            overlayGranted = Settings.canDrawOverlays(context)
        )
    }

    fun toggleBlocked(app: InstalledAppInfo) {
        viewModelScope.launch {
            val blocked = app.packageName in _state.value.blocked
            repository.setBlocked(app.packageName, app.label, !blocked)
            load()
        }
    }

    fun startBlockerService() {
        val intent = Intent(context, AppBlockerService::class.java)
        context.startForegroundService(intent)
    }

    fun stopBlockerService() {
        val intent = Intent(context, AppBlockerService::class.java).apply {
            action = AppBlockerService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun hasUsageAccess(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return !stats.isNullOrEmpty()
    }
}
