package com.jongwook.siteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class InspectionScheduleEntry(
    val id: Long,
    val projectTitle: String,
    val baseDate: String,
    val intervalMonths: Int,
    val note: String = "정기 점검"
)

data class InspectionScheduleSnapshot(
    val entry: InspectionScheduleEntry,
    val nextDate: Date,
    val nextDateText: String,
    val daysUntil: Long
)

object InspectionScheduleStore {
    private const val PREF_NAME = "SiteboardInspectionSchedules"
    private const val KEY_ITEMS = "inspection_items"
    private const val KEY_LAST_ALERTS = "inspection_last_alerts"
    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun nextId(): Long = System.currentTimeMillis()

    fun getAll(context: Context): List<InspectionScheduleEntry> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    InspectionScheduleEntry(
                        id = item.optLong("id"),
                        projectTitle = item.optString("projectTitle"),
                        baseDate = item.optString("baseDate"),
                        intervalMonths = item.optInt("intervalMonths", 3),
                        note = item.optString("note", "정기 점검")
                    )
                )
            }
        }
    }

    fun save(context: Context, entry: InspectionScheduleEntry) {
        val items = getAll(context).toMutableList()
        val index = items.indexOfFirst { it.id == entry.id }
        if (index >= 0) items[index] = entry else items.add(entry)
        writeItems(context, items)
    }

    fun delete(context: Context, entryId: Long) {
        writeItems(context, getAll(context).filterNot { it.id == entryId })
        val alerts = JSONObject(prefs(context).getString(KEY_LAST_ALERTS, "{}") ?: "{}")
        alerts.remove(entryId.toString())
        prefs(context).edit().putString(KEY_LAST_ALERTS, alerts.toString()).apply()
    }

    fun getUpcoming(context: Context, limit: Int = 3): List<InspectionScheduleSnapshot> {
        val today = startOfDay(Date())
        return getAll(context)
            .mapNotNull { entry ->
                val nextDate = resolveNextOccurrence(entry, today) ?: return@mapNotNull null
                InspectionScheduleSnapshot(
                    entry = entry,
                    nextDate = nextDate,
                    nextDateText = dateFormat.format(nextDate),
                    daysUntil = (startOfDay(nextDate).time - today.time) / ONE_DAY_MILLIS
                )
            }
            .sortedBy { it.nextDate.time }
            .take(limit)
    }

    fun consumeDueNotificationMessages(context: Context): List<String> {
        val alerts = JSONObject(prefs(context).getString(KEY_LAST_ALERTS, "{}") ?: "{}")
        val today = startOfDay(Date())
        val messages = mutableListOf<String>()

        getUpcoming(context, limit = 20).forEach { snapshot ->
            val triggerKey = when (snapshot.daysUntil) {
                14L -> "D-14"
                7L -> "D-7"
                1L -> "D-1"
                0L -> "D-DAY"
                -7L -> "OVERDUE-7"
                else -> null
            } ?: return@forEach

            val entryKey = snapshot.entry.id.toString()
            val todayKey = "${dateFormat.format(today)}:$triggerKey"
            if (alerts.optString(entryKey) == todayKey) return@forEach

            val message = when {
                snapshot.daysUntil > 0 -> "${snapshot.entry.projectTitle} · ${snapshot.entry.note} $triggerKey (${snapshot.nextDateText})"
                snapshot.daysUntil == 0L -> "${snapshot.entry.projectTitle} · 오늘 ${snapshot.entry.note} 예정"
                else -> "${snapshot.entry.projectTitle} · ${snapshot.entry.note} 일정 확인 필요"
            }
            alerts.put(entryKey, todayKey)
            messages.add(message)
        }

        if (messages.isNotEmpty()) {
            prefs(context).edit().putString(KEY_LAST_ALERTS, alerts.toString()).apply()
        }
        return messages
    }

    fun formatIntervalLabel(intervalMonths: Int): String {
        return when (intervalMonths) {
            1 -> "월별"
            3 -> "분기별"
            6 -> "반기별"
            12 -> "연간"
            else -> "${intervalMonths}개월"
        }
    }

    private fun writeItems(context: Context, items: List<InspectionScheduleEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("projectTitle", entry.projectTitle)
                    put("baseDate", entry.baseDate)
                    put("intervalMonths", entry.intervalMonths)
                    put("note", entry.note)
                }
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun resolveNextOccurrence(entry: InspectionScheduleEntry, today: Date): Date? {
        val base = runCatching { dateFormat.parse(entry.baseDate) }.getOrNull() ?: return null
        val calendar = Calendar.getInstance().apply { time = startOfDay(base) }
        while (calendar.time.before(today)) {
            calendar.add(Calendar.MONTH, entry.intervalMonths)
        }
        return calendar.time
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
