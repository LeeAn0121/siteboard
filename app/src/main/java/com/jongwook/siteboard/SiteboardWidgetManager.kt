package com.jongwook.siteboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WidgetSnapshot(
    val totalCount: Int,
    val projectCount: Int,
    val todayCount: Int,
    val recentProject: String,
    val recentProjectCount: Int,
    val recentDate: String,
    val lastUpdated: String
)

object SiteboardWidgetManager {
    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val snapshot = loadSnapshot(context)
            updateQuickAddWidget(context)
            updateStatsWidget(context, snapshot)
            updateProjectWidget(context, snapshot)
        }
    }

    fun updateQuickAddWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, QuickAddWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            val launchIntent = Intent(context, SubActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val archiveIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ARCHIVE)
            }
            val archivePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 10,
                archiveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetQuickAddRoot, pendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetQuickAdd, pendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenArchive, archivePendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun updateStatsWidget(context: Context, snapshot: WidgetSnapshot) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            views.setTextViewText(R.id.tvWidgetTodayCount, snapshot.todayCount.toString())
            views.setTextViewText(R.id.tvWidgetTotalCount, snapshot.totalCount.toString())
            views.setTextViewText(R.id.tvWidgetProjectCount, snapshot.projectCount.toString())
            views.setTextViewText(R.id.tvWidgetRecentProject, "최근 현장 · ${snapshot.recentProject}")
            views.setTextViewText(R.id.tvWidgetUpdatedAt, snapshot.lastUpdated)
            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val addIntent = Intent(context, SubActivity::class.java)
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1001,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val archiveIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ARCHIVE)
            }
            val archivePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1002,
                archiveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1003,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetStatsRoot, pendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenHome, pendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenArchiveMini, archivePendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetAddRecordMini, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetRefreshMini, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun updateProjectWidget(context: Context, snapshot: WidgetSnapshot) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ProjectShortcutWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_project_shortcut)
            views.setTextViewText(R.id.tvWidgetProjectTitle, snapshot.recentProject)
            views.setTextViewText(R.id.tvWidgetProjectMeta, "사진 ${snapshot.recentProjectCount}장 · ${snapshot.recentDate}")
            views.setTextViewText(R.id.tvWidgetProjectUpdatedAt, snapshot.lastUpdated)

            val openIntent = Intent(context, ProjectDetailActivity::class.java).apply {
                putExtra("PROJECT_TITLE", snapshot.recentProject)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetOpenProject, openPendingIntent)
            val addIntent = Intent(context, SubActivity::class.java).apply {
                putExtra(SubActivity.EXTRA_PREFILL_TITLE, snapshot.recentProject)
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2500,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val exportIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_EXPORT_LATEST_PROJECT_PDF
                putExtra(ProjectWidgetActionReceiver.EXTRA_PROJECT_TITLE, snapshot.recentProject)
            }
            val exportPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3000,
                exportIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetExportPdf, exportPendingIntent)
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3500,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetAddSameProject, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetProjectRefresh, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun loadSnapshot(context: Context): WidgetSnapshot {
        val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayCount = posts.count { it.date.startsWith(today) }
        val recentProject = posts.firstOrNull()?.title ?: "기록 없음"
        val recentProjectPosts = posts.filter { it.title == recentProject }
        val recentDate = recentProjectPosts.firstOrNull()?.date ?: "최근 기록 없음"
        val lastUpdated = "업데이트 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        return WidgetSnapshot(
            totalCount = posts.size,
            projectCount = posts.map { it.title.trim() }.filter { it.isNotEmpty() }.distinct().size,
            todayCount = todayCount,
            recentProject = recentProject,
            recentProjectCount = recentProjectPosts.size,
            recentDate = recentDate,
            lastUpdated = lastUpdated
        )
    }
}
