package com.jongwook.siteboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProjectWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH_WIDGETS) {
            SiteboardWidgetManager.refreshAll(context.applicationContext)
            SiteboardNotificationManager.showStatusNotification(
                context.applicationContext,
                "위젯 갱신",
                "홈 화면 위젯 정보를 최신 상태로 새로고침했습니다.",
                4100
            )
            return
        }
        if (intent?.action != ACTION_EXPORT_LATEST_PROJECT_PDF) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val projectTitle = intent.getStringExtra(EXTRA_PROJECT_TITLE).orEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val posts = AppDatabase.getDatabase(appContext).postDao().getAllPostsOnce()
                    .filter { it.title == projectTitle }
                if (posts.isNotEmpty()) {
                    val file = SiteboardPdfExporter.exportProjectPdf(appContext, projectTitle, posts)
                    SiteboardNotificationManager.showStatusNotification(
                        appContext,
                        "PDF 저장 완료",
                        "[$projectTitle] 보고서를 ${file.name}으로 저장했습니다.",
                        4101
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXPORT_LATEST_PROJECT_PDF = "com.jongwook.siteboard.EXPORT_LATEST_PROJECT_PDF"
        const val ACTION_REFRESH_WIDGETS = "com.jongwook.siteboard.REFRESH_WIDGETS"
        const val EXTRA_PROJECT_TITLE = "project_title"
    }
}
