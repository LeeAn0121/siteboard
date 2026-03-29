package com.jongwook.siteboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProjectWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    ACTION_REFRESH_WIDGETS -> {
                        SiteboardWidgetManager.refreshAll(appContext)
                    }
                    ACTION_EXPORT_LATEST_PROJECT_PDF -> {
                        val projectTitle = intent.getStringExtra(EXTRA_PROJECT_TITLE).orEmpty()
                        val posts = AppDatabase.getDatabase(appContext).postDao().getAllPostsOnce()
                            .filter { it.title == projectTitle }
                        if (posts.isNotEmpty()) {
                            val file = SiteboardPdfExporter.exportProjectPdf(appContext, projectTitle, posts)
                            SiteboardNotificationManager.showPdfCompleteNotification(
                                appContext,
                                "[$projectTitle] 보고서를 ${file.name}으로 저장했습니다.",
                                4101
                            )
                        }
                    }
                    ACTION_COMPLETE_INSPECTION -> {
                        val entryId = intent.getLongExtra(EXTRA_INSPECTION_ENTRY_ID, -1L)
                        val updated = InspectionScheduleStore.markCompleted(appContext, entryId)
                        if (updated != null) {
                            SiteboardNotificationManager.showStatusNotification(
                                appContext,
                                "점검 완료 처리",
                                "[${updated.projectTitle}] 다음 ${updated.note} 일정을 ${updated.baseDate} 기준으로 갱신했습니다.",
                                4102
                            )
                            SiteboardWidgetManager.refreshAll(appContext)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXPORT_LATEST_PROJECT_PDF = "com.jongwook.siteboard.EXPORT_LATEST_PROJECT_PDF"
        const val ACTION_REFRESH_WIDGETS = "com.jongwook.siteboard.REFRESH_WIDGETS"
        const val ACTION_COMPLETE_INSPECTION = "com.jongwook.siteboard.COMPLETE_INSPECTION"
        const val EXTRA_PROJECT_TITLE = "project_title"
        const val EXTRA_INSPECTION_ENTRY_ID = "inspection_entry_id"
    }
}
