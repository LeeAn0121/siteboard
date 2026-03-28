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
            val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
            val snapshot = loadSnapshot(posts)
            updateQuickAddWidget(context)
            updateStatsWidget(context, snapshot)
            updateProjectWidget(context, posts, snapshot)
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
            val homePendingIntent = PendingIntent.getActivity(
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

            views.setOnClickPendingIntent(R.id.widgetStatsRoot, homePendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenHome, homePendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenArchiveMini, archivePendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetAddRecordMini, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetRefreshMini, refreshPendingIntent)

            applyStatsWidgetSizing(appWidgetManager.getAppWidgetOptions(appWidgetId), views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun updateProjectWidget(context: Context, posts: List<PostEntity>, fallbackSnapshot: WidgetSnapshot) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ProjectShortcutWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val selectedTitle = WidgetPreferences.getSelectedProject(context, appWidgetId)
            val title = resolveProjectTitle(posts, selectedTitle, fallbackSnapshot.recentProject)
            val projectPosts = posts.filter { it.title == title }
            val recentDate = projectPosts.firstOrNull()?.date ?: "최근 기록 없음"
            val updatedAt = "업데이트 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"

            val views = RemoteViews(context.packageName, R.layout.widget_project_shortcut)
            views.setTextViewText(R.id.tvWidgetProjectTitle, title)
            views.setTextViewText(R.id.tvWidgetProjectMeta, "사진 ${projectPosts.size}장 · ${recentDate}")
            views.setTextViewText(R.id.tvWidgetProjectUpdatedAt, updatedAt)

            val openIntent = Intent(context, ProjectDetailActivity::class.java).apply {
                putExtra("PROJECT_TITLE", title)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val addIntent = Intent(context, SubActivity::class.java).apply {
                putExtra(SubActivity.EXTRA_PREFILL_TITLE, title)
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2500,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val exportIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_EXPORT_LATEST_PROJECT_PDF
                putExtra(ProjectWidgetActionReceiver.EXTRA_PROJECT_TITLE, title)
            }
            val exportPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3000,
                exportIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3500,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.btnWidgetOpenProject, openPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetAddSameProject, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetExportPdf, exportPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetProjectRefresh, refreshPendingIntent)

            applyProjectWidgetSizing(appWidgetManager.getAppWidgetOptions(appWidgetId), views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun resolveProjectTitle(posts: List<PostEntity>, selectedTitle: String?, fallbackTitle: String): String {
        return when {
            !selectedTitle.isNullOrBlank() && posts.any { it.title == selectedTitle } -> selectedTitle
            posts.any { it.title == fallbackTitle } -> fallbackTitle
            else -> posts.firstOrNull()?.title ?: "기록 없음"
        }
    }

    private fun loadSnapshot(posts: List<PostEntity>): WidgetSnapshot {
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

    private fun applyStatsWidgetSizing(options: Bundle, views: RemoteViews) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val showActionRow = minWidth >= 220 && minHeight >= 110
        views.setViewVisibility(R.id.btnWidgetOpenHome, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetOpenArchiveMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetAddRecordMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetRefreshMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
    }

    private fun applyProjectWidgetSizing(options: Bundle, views: RemoteViews) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val showExtra = minWidth >= 240 && minHeight >= 130
        views.setViewVisibility(R.id.btnWidgetAddSameProject, if (showExtra) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetProjectRefresh, if (showExtra) android.view.View.VISIBLE else android.view.View.GONE)
    }
}
