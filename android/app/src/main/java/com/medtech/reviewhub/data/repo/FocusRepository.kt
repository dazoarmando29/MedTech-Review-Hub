package com.medtech.reviewhub.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.medtech.reviewhub.data.db.BlockedAppEntity
import com.medtech.reviewhub.data.db.FocusDao
import com.medtech.reviewhub.data.db.FocusSessionEntity
import com.medtech.reviewhub.domain.model.FocusSession
import com.medtech.reviewhub.domain.model.InstalledAppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Focus-mode data: completed sessions + the user-chosen distraction app list.
 * All monitoring is explicit and user-controlled (see AppBlockerService).
 */
class FocusRepository(private val context: Context, private val dao: FocusDao) {

    fun observeBlockedApps(): Flow<List<InstalledAppInfo>> =
        dao.blockedApps().map { list -> list.map { InstalledAppInfo(it.packageName, it.label) } }

    suspend fun blockedAppsOnce(): List<InstalledAppInfo> =
        dao.blockedAppsOnce().map { InstalledAppInfo(it.packageName, it.label) }

    suspend fun setBlocked(packageName: String, label: String, blocked: Boolean) {
        if (blocked) dao.upsertBlockedApp(BlockedAppEntity(packageName, label))
        else dao.deleteBlockedApp(BlockedAppEntity(packageName, label))
    }

    suspend fun clearBlockedApps() = dao.clearBlockedApps()

    /** Launcher apps available for the distraction-app picker. */
    fun installedLauncherApps(): List<InstalledAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolved
            .map { InstalledAppInfo(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun recordSession(startedAtMs: Long, endedAtMs: Long, plannedMinutes: Int,
                              completedMinutes: Int, presetLabel: String, interruptions: Int) {
        dao.insertSession(
            FocusSessionEntity(
                id = UUID.randomUUID().toString(),
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                plannedMinutes = plannedMinutes,
                completedMinutes = completedMinutes,
                presetLabel = presetLabel,
                interruptions = interruptions
            )
        )
    }

    suspend fun recentSessions(limit: Int = 20): List<FocusSession> =
        dao.recent(limit).map {
            FocusSession(it.id, it.startedAtMs, it.endedAtMs, it.plannedMinutes, it.completedMinutes, it.presetLabel, it.interruptions)
        }

    suspend fun focusMinutesToday(todayStartMs: Long): Int = dao.minutesSince(todayStartMs)
}
