package com.jongwook.siteboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object SiteboardNotificationManager {
    const val REMINDER_CHANNEL_ID = "siteboard_reminder"
    const val STATUS_CHANNEL_ID = "siteboard_status"
    const val REMINDER_NOTIFICATION_ID = 2001
    const val INSPECTION_NOTIFICATION_ID = 2011

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "기록 리마인더",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "매일 현장 기록 작성을 알려줍니다."
        }
        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "작업 상태",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "PDF 내보내기와 위젯 작업 결과를 알려줍니다."
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(statusChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showReminderNotification(context: Context) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val hasTodayRecord = posts.any { it.date.startsWith(today) }
        val isMissedDay = !hasTodayRecord && NotificationPreferences.isMissedDayEnabled(context)
        if (!hasTodayRecord && !NotificationPreferences.isMissedDayEnabled(context)) return

        val intent = Intent(context, SubActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messageLines = mutableListOf<String>()
        messageLines.add(if (isMissedDay) "오늘 저장된 현장 기록이 없습니다. 지금 바로 남겨두세요." else "오늘 현장 기록을 남길 시간이 되었습니다.")
        messageLines.addAll(buildMissingProjectMessages(context, posts))

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (isMissedDay) "SITEBOARD 미기록 경고" else "SITEBOARD 기록 알림")
            .setContentText(messageLines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageLines.joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun showInspectionReminderNotifications(context: Context) {
        if (!canPostNotifications(context)) return
        if (!NotificationPreferences.isInspectionReminderEnabled(context)) return
        ensureChannels(context)

        val messages = InspectionScheduleStore.consumeDueNotificationMessages(context)
        if (messages.isEmpty()) return

        val intent = Intent(context, InspectionScheduleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3111,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("SITEBOARD 점검 일정 알림")
            .setContentText(messages.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(messages.joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(INSPECTION_NOTIFICATION_ID, notification)
    }

    fun showStatusNotification(context: Context, title: String, message: String, notificationId: Int) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showSaveSuccessNotification(context: Context, message: String, notificationId: Int) {
        if (!NotificationPreferences.isSaveSuccessEnabled(context)) return
        showStatusNotification(context, "기록 저장 완료", message, notificationId)
    }

    fun showPdfCompleteNotification(context: Context, message: String, notificationId: Int) {
        if (!NotificationPreferences.isPdfCompleteEnabled(context)) return
        showStatusNotification(context, "PDF 저장 완료", message, notificationId)
    }

    fun showStorageFullNotification(context: Context) {
        showStatusNotification(
            context,
            "구글 드라이브 용량 부족",
            "용량이 꽉 차서 백업을 완료할 수 없습니다. 드라이브 공간을 확보해주세요.",
            3001
        )
    }

    private fun buildMissingProjectMessages(context: Context, posts: List<PostEntity>): List<String> {
        val grouped = posts.groupBy { it.title }
        return ProjectMetaStore.getFavoriteProjects(context).mapNotNull { title ->
            val recentPost = grouped[title]?.maxByOrNull { it.id } ?: return@mapNotNull null
            val daysAgo = daysSince(recentPost.date)
            if (daysAgo >= 7) "즐겨찾기 현장 [$title] 기록이 ${daysAgo}일째 없습니다." else null
        }.take(2)
    }

    private fun daysSince(rawDate: String): Long {
        val patterns = listOf("yyyy-MM-dd", "yyyy.MM.dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss")
        val parsed = patterns.firstNotNullOfOrNull { pattern ->
            runCatching { java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).parse(rawDate) }.getOrNull()
        } ?: return 0
        return ((System.currentTimeMillis() - parsed.time) / (24L * 60L * 60L * 1000L)).coerceAtLeast(0)
    }
}

