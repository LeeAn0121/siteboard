package com.jongwook.siteboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

data class TodayBoardSnapshot(
    val todayCount: Int,
    val inspectionCount: Int,
    val favoriteProject: String,
    val repairProject: String,
    val focusLine: String
)

object SiteboardWidgetManager {
    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
            val snapshot = loadSnapshot(posts)
            updateQuickAddWidget(context)
            updateStatsWidget(context, snapshot)
            updateProjectWidget(context, posts, snapshot)
            updateInspectionWidget(context)
            updateTodayBoardWidget(context, posts, snapshot)
        }
    }

    fun updateQuickAddWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, QuickAddWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            val launchIntent = Intent(context, SubActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.quick_add.$appWidgetId"
                data = Uri.parse("siteboard://widget/quick-add/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val archiveIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.quick_add.archive.$appWidgetId"
                data = Uri.parse("siteboard://widget/quick-add/archive/$appWidgetId")
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ARCHIVE)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
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

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.stats.home.$appWidgetId"
                data = Uri.parse("siteboard://widget/stats/home/$appWidgetId")
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val homePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val addIntent = Intent(context, SubActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.stats.add.$appWidgetId"
                data = Uri.parse("siteboard://widget/stats/add/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1001,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val archiveIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.stats.archive.$appWidgetId"
                data = Uri.parse("siteboard://widget/stats/archive/$appWidgetId")
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ARCHIVE)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val archivePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1002,
                archiveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
                data = Uri.parse("siteboard://widget/stats/refresh/$appWidgetId")
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
                action = "com.jongwook.siteboard.widget.project.open.$appWidgetId"
                data = Uri.parse("siteboard://widget/project/open/$appWidgetId")
                putExtra("PROJECT_TITLE", title)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val addIntent = Intent(context, SubActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.project.add.$appWidgetId"
                data = Uri.parse("siteboard://widget/project/add/$appWidgetId")
                putExtra(SubActivity.EXTRA_PREFILL_TITLE, title)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 2500,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val exportIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_EXPORT_LATEST_PROJECT_PDF
                data = Uri.parse("siteboard://widget/project/export/$appWidgetId")
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
                data = Uri.parse("siteboard://widget/project/refresh/$appWidgetId")
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3500,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetProjectRoot, openPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetOpenProject, openPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetAddSameProject, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetExportPdf, exportPendingIntent)
            views.setOnClickPendingIntent(R.id.btnWidgetProjectRefresh, refreshPendingIntent)

            applyProjectWidgetSizing(appWidgetManager.getAppWidgetOptions(appWidgetId), views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun updateInspectionWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, InspectionWidgetProvider::class.java))
        val upcoming = InspectionScheduleStore.getUpcoming(context, limit = 3)
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_inspection_schedule)
            views.setTextViewText(
                R.id.tvInspectionWidgetHeadline,
                if (upcoming.isEmpty()) "점검 일정을 등록해 주세요" else "다가오는 점검 ${upcoming.size}건"
            )
            views.setTextViewText(R.id.tvInspectionItemOne, formatInspectionLine(upcoming.getOrNull(0)))
            views.setTextViewText(R.id.tvInspectionItemTwo, formatInspectionLine(upcoming.getOrNull(1)))
            views.setTextViewText(R.id.tvInspectionItemThree, formatInspectionLine(upcoming.getOrNull(2)))

            val manageIntent = Intent(context, InspectionScheduleActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.inspection.manage.$appWidgetId"
                data = Uri.parse("siteboard://widget/inspection/manage/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val managePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 4000,
                manageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val archiveIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.inspection.archive.$appWidgetId"
                data = Uri.parse("siteboard://widget/inspection/archive/$appWidgetId")
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ARCHIVE)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val archivePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 4001,
                archiveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
                data = Uri.parse("siteboard://widget/inspection/refresh/$appWidgetId")
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 4002,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val completeIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_COMPLETE_INSPECTION
                data = Uri.parse("siteboard://widget/inspection/complete/$appWidgetId")
                putExtra(ProjectWidgetActionReceiver.EXTRA_INSPECTION_ENTRY_ID, upcoming.firstOrNull()?.entry?.id ?: -1L)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 4003,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendarIntent = Intent(context, InspectionCalendarActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.inspection.calendar.$appWidgetId"
                data = Uri.parse("siteboard://widget/inspection/calendar/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val calendarPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 4004,
                calendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetInspectionRoot, managePendingIntent)
            views.setOnClickPendingIntent(R.id.btnInspectionOpenManager, managePendingIntent)
            views.setOnClickPendingIntent(R.id.btnInspectionOpenArchive, archivePendingIntent)
            views.setOnClickPendingIntent(R.id.btnInspectionRefresh, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.btnInspectionComplete, completePendingIntent)
            views.setOnClickPendingIntent(R.id.tvInspectionItemOne, calendarPendingIntent)

            applyInspectionWidgetSizing(appWidgetManager.getAppWidgetOptions(appWidgetId), views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    fun updateTodayBoardWidget(context: Context, posts: List<PostEntity>, snapshot: WidgetSnapshot) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayBoardWidgetProvider::class.java))
        val todayBoard = loadTodayBoardSnapshot(context, posts, snapshot)
        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_today_board)
            views.setTextViewText(R.id.tvTodayBoardHeadline, "점검 ${todayBoard.inspectionCount}건 · 오늘 ${todayBoard.todayCount}건")
            views.setTextViewText(R.id.tvTodayBoardLineOne, todayBoard.focusLine)
            views.setTextViewText(R.id.tvTodayBoardLineTwo, "즐겨찾기 · ${todayBoard.favoriteProject}")
            views.setTextViewText(R.id.tvTodayBoardLineThree, "보수 필요 · ${todayBoard.repairProject}")

            val addIntent = Intent(context, SubActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.today.add.$appWidgetId"
                data = Uri.parse("siteboard://widget/today/add/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 5000,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendarIntent = Intent(context, InspectionCalendarActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.today.calendar.$appWidgetId"
                data = Uri.parse("siteboard://widget/today/calendar/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val calendarPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 5001,
                calendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = Intent(context, ProjectWidgetActionReceiver::class.java).apply {
                action = ProjectWidgetActionReceiver.ACTION_REFRESH_WIDGETS
                data = Uri.parse("siteboard://widget/today/refresh/$appWidgetId")
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 5002,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val homeIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.jongwook.siteboard.widget.today.home.$appWidgetId"
                data = Uri.parse("siteboard://widget/today/home/$appWidgetId")
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val homePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 5003,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetTodayBoardRoot, homePendingIntent)
            views.setOnClickPendingIntent(R.id.btnTodayBoardAdd, addPendingIntent)
            views.setOnClickPendingIntent(R.id.btnTodayBoardCalendar, calendarPendingIntent)
            views.setOnClickPendingIntent(R.id.btnTodayBoardRefresh, refreshPendingIntent)
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

    private fun loadTodayBoardSnapshot(context: Context, posts: List<PostEntity>, fallbackSnapshot: WidgetSnapshot): TodayBoardSnapshot {
        val now = Date()
        val todayPatterns = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(now),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(now)
        )
        val todayCount = posts.count { post -> todayPatterns.any { post.date.contains(it) } }
        val inspectionItems = InspectionScheduleStore.getUpcoming(context, limit = 20)
        val favoriteProject = ProjectMetaStore.getFavoriteProjects(context).firstOrNull() ?: fallbackSnapshot.recentProject
        val repairProject = ProjectMetaStore.getProjectsByStatus(context, ProjectMeta.STATUS_REPAIR).firstOrNull() ?: "없음"
        val focusLine = inspectionItems.firstOrNull()?.let {
            "${it.entry.projectTitle} · ${it.entry.note} · ${if (it.daysUntil > 0) "D-${it.daysUntil}" else if (it.daysUntil == 0L) "오늘" else "${kotlin.math.abs(it.daysUntil)}일 지남"}"
        } ?: "최근 현장 · ${fallbackSnapshot.recentProject}"
        return TodayBoardSnapshot(
            todayCount = todayCount,
            inspectionCount = inspectionItems.size,
            favoriteProject = favoriteProject,
            repairProject = repairProject,
            focusLine = focusLine
        )
    }

    private fun applyStatsWidgetSizing(options: Bundle, views: RemoteViews) {
        val columns = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0))
        val rows = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
        val showActionRow = columns >= 4 && rows >= 2
        views.setViewVisibility(R.id.btnWidgetOpenHome, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetOpenArchiveMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetAddRecordMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetRefreshMini, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
    }

    private fun applyProjectWidgetSizing(options: Bundle, views: RemoteViews) {
        val columns = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0))
        val rows = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
        val showExtra = columns >= 4 && rows >= 2
        views.setViewVisibility(R.id.btnWidgetAddSameProject, if (showExtra) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnWidgetProjectRefresh, if (showExtra) android.view.View.VISIBLE else android.view.View.GONE)
    }

    private fun applyInspectionWidgetSizing(options: Bundle, views: RemoteViews) {
        val columns = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0))
        val rows = estimateSpan(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
        val showActionRow = columns >= 4 && rows >= 2
        views.setViewVisibility(R.id.layoutInspectionWidgetActions, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btnInspectionComplete, if (showActionRow) android.view.View.VISIBLE else android.view.View.GONE)
    }

    private fun estimateSpan(sizeDp: Int): Int {
        if (sizeDp <= 0) return 1
        return ((sizeDp + 30) / 70).coerceAtLeast(1)
    }

    private fun formatInspectionLine(snapshot: InspectionScheduleSnapshot?): String {
        return snapshot?.let {
            val countdown = when {
                it.daysUntil > 0 -> "D-${it.daysUntil}"
                it.daysUntil == 0L -> "오늘"
                else -> "${kotlin.math.abs(it.daysUntil)}일 지남"
            }
            "${it.entry.projectTitle} · ${it.entry.note} · ${countdown}"
        } ?: "-"
    }
}
